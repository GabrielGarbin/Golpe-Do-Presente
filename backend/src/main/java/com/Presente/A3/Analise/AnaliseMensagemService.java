package com.Presente.A3.Analise;

import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Modelo.Confianca;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.NivelRisco;
import com.Presente.A3.Modelo.StatusGolpista;
import com.Presente.A3.Service.GolpistaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra a análise de uma mensagem em até quatro camadas:
 *
 * <ol>
 *   <li><b>Regras</b> ({@link AnalisadorRegras}): palavras-chave com pesos;</li>
 *   <li><b>Classificador local</b> ({@link ClassificadorLocal}): semelhança com mensagens de golpe;</li>
 *   <li><b>Base colaborativa</b> ({@link GolpistaService}): remetente já denunciado, se informado;</li>
 *   <li><b>IA externa</b> ({@link AnalisadorIA}), <i>opcional</i>: só consultada quando as demais ficam em dúvida.</li>
 * </ol>
 *
 * <p>Nota local = {@code PESO_REGRAS * regras + (1 - PESO_REGRAS) * classificador}.
 * O classificador sozinho, sem nenhuma regra, não chega a ALTO: é preciso
 * concordância entre as camadas. Se a IA responder, ela entra com peso
 * {@code app.ia.peso} (padrão 0,5) na zona de dúvida. Se a IA não existir
 * ou falhar, o resultado é exatamente o das camadas locais.</p>
 */
@Service
public class AnaliseMensagemService {

    static final int LIMIAR_ALTO = 60;
    static final int LIMIAR_MEDIO = 30;
    /** Peso das regras na nota local (o do classificador é o complemento). */
    static final double PESO_REGRAS = 0.5;
    /** Faixa de nota local em que vale a pena consultar a IA. */
    static final int ZONA_DUVIDA_INFERIOR = 20;
    static final int ZONA_DUVIDA_SUPERIOR = 70;
    private static final int PESO_REMETENTE_CONFIRMADO = 50;
    private static final int PESO_REMETENTE_SUSPEITO = 25;

    private final AnalisadorRegras regras;
    private final ClassificadorLocal classificador;
    private final GolpistaService golpistaService;
    private final Optional<AnalisadorIA> ia;
    private final double pesoIa;

    public AnaliseMensagemService(AnalisadorRegras regras,
                                  ClassificadorLocal classificador,
                                  GolpistaService golpistaService,
                                  Optional<AnalisadorIA> ia,
                                  @Value("${app.ia.peso:0.5}") double pesoIa) {
        this.regras = regras;
        this.classificador = classificador;
        this.golpistaService = golpistaService;
        this.ia = ia;
        this.pesoIa = Math.max(0.0, Math.min(1.0, pesoIa));
    }

    /** @return true se há uma IA externa configurada (informativo, para o endpoint de status) */
    public boolean iaDisponivel() {
        return ia.isPresent();
    }

    /**
     * @param mensagem          texto recebido (obrigatório)
     * @param telefoneRemetente número de quem enviou (opcional)
     * @param solicitante       quem pediu a análise (ex.: IP), para limitar o uso da IA
     * @throws IllegalArgumentException mensagem vazia ou telefone inválido
     */
    public ResultadoVerificacao analisar(String mensagem, String telefoneRemetente, String solicitante) {
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("A mensagem a ser analisada é obrigatória.");
        }
        List<String> camadas = new ArrayList<>();
        List<String> motivos = new ArrayList<>();

        // Camada 1: regras
        AnalisadorRegras.ResultadoRegras r = regras.analisar(mensagem);
        camadas.add("REGRAS");
        motivos.addAll(r.motivos());

        // Camada 2: classificador local
        ClassificadorLocal.Resultado c = classificador.classificar(mensagem);
        camadas.add("CLASSIFICADOR_LOCAL");
        if (c.temBase()) {
            motivos.add("O texto é " + c.pontos() + "% parecido com mensagens de golpe já conhecidas.");
        } else {
            motivos.add("O texto tem poucas palavras conhecidas; o classificador não tem base para opinar.");
        }

        int base = (int) Math.round(PESO_REGRAS * r.pontos() + (1 - PESO_REGRAS) * c.pontos());
        boolean regrasAcusam = r.pontos() >= LIMIAR_MEDIO;
        boolean classificadorAcusa = c.pontos() >= 50;
        boolean discordam = c.temBase() && regrasAcusam != classificadorAcusa;
        boolean semBase = !c.temBase() && r.pontos() == 0;
        boolean inconclusivo = semBase || discordam;

        // Camada 3: base colaborativa (remetente), se informado
        int pontos = base;
        boolean remetenteConfirmado = false;
        if (telefoneRemetente != null && !telefoneRemetente.isBlank()) {
            camadas.add("BASE_COLABORATIVA");
            Optional<Golpista> remetente = golpistaService.findByTelefone(telefoneRemetente);
            if (remetente.isPresent()) {
                if (remetente.get().getStatus() == StatusGolpista.CONFIRMADO) {
                    remetenteConfirmado = true;
                    pontos += PESO_REMETENTE_CONFIRMADO;
                    motivos.add("O remetente consta na blacklist colaborativa (+" + PESO_REMETENTE_CONFIRMADO + ")");
                } else {
                    pontos += PESO_REMETENTE_SUSPEITO;
                    motivos.add("O remetente já foi denunciado, mas ainda não confirmado (+"
                            + PESO_REMETENTE_SUSPEITO + ")");
                }
                inconclusivo = false; // há um dado concreto sobre o remetente
            }
        }

        // Camada 4 (opcional): IA, só na zona de dúvida e nunca contra a blacklist
        boolean iaConsultada = false;
        Confianca confianca = calcularConfianca(base, regrasAcusam, classificadorAcusa, c.temBase());
        if (ia.isPresent() && !remetenteConfirmado
                && (inconclusivo || (pontos >= ZONA_DUVIDA_INFERIOR && pontos < ZONA_DUVIDA_SUPERIOR))) {
            Optional<AnalisadorIA.Parecer> parecer = ia.get().analisar(mensagem, solicitante);
            if (parecer.isPresent()) {
                iaConsultada = true;
                camadas.add("IA");
                int notaIa = parecer.get().pontos();
                pontos = (int) Math.round((1 - pesoIa) * pontos + pesoIa * notaIa);
                motivos.add("Análise por IA: " + notaIa + "/100"
                        + (parecer.get().justificativa().isBlank() ? "" : " - " + parecer.get().justificativa()));
                inconclusivo = false;
                confianca = (notaIa >= 50) == (base >= LIMIAR_MEDIO) ? Confianca.ALTA : Confianca.BAIXA;
            }
        }

        if (remetenteConfirmado) {
            pontos = Math.max(pontos, LIMIAR_ALTO); // coerência entre nota e nível
        }
        pontos = Math.max(0, Math.min(100, pontos));

        NivelRisco nivel = remetenteConfirmado || pontos >= LIMIAR_ALTO ? NivelRisco.ALTO
                : pontos >= LIMIAR_MEDIO ? NivelRisco.MEDIO : NivelRisco.BAIXO;

        if (r.motivos().isEmpty() && !c.temBase()) {
            motivos.add("Nenhum padrão típico do golpe foi identificado no texto.");
        }
        motivos.add("A nota combina as camadas: " + String.join(", ", camadas).toLowerCase().replace('_', ' ') + ".");

        if (inconclusivo) {
            confianca = Confianca.BAIXA;
            motivos.add("Sem dados suficientes para uma conclusão confiável: as camadas de análise "
                    + (discordam ? "discordam entre si." : "tiveram pouco texto para avaliar."));
        } else if (remetenteConfirmado) {
            confianca = Confianca.ALTA;
        }
        return new ResultadoVerificacao(nivel, pontos, motivos,
                recomendacoes(nivel, inconclusivo), confianca, inconclusivo, camadas);
    }

    private static Confianca calcularConfianca(int base, boolean regrasAcusam,
                                               boolean classificadorAcusa, boolean temBase) {
        if (!temBase) {
            return Confianca.BAIXA;
        }
        boolean concordam = regrasAcusam == classificadorAcusa;
        if (concordam && (base >= LIMIAR_ALTO + 10 || base < 15)) {
            return Confianca.ALTA;
        }
        return concordam ? Confianca.MEDIA : Confianca.BAIXA;
    }

    /** Recomendações padrão conforme o nível de risco (e aviso extra se inconclusivo). */
    static List<String> recomendacoes(NivelRisco nivel, boolean inconclusivo) {
        List<String> r = new ArrayList<>();
        switch (nivel) {
            case ALTO -> {
                r.add("Não pague nada, não informe dados de cartão e não clique em links.");
                r.add("Confirme com a loja pelos canais oficiais, usando um contato que você mesmo procurou.");
                r.add("Denuncie o número para proteger outras pessoas.");
            }
            case MEDIO -> {
                r.add("Desconfie: confirme a informação com a loja por um canal oficial antes de qualquer pagamento.");
                r.add("Lembre-se de que nenhuma loja legítima cobra para entregar um brinde.");
            }
            default -> {
                r.add("Nenhum sinal forte encontrado, mas a ausência de alertas não garante que seja legítimo.");
                r.add("Em caso de dúvida, confirme com a loja por um canal oficial.");
            }
        }
        if (inconclusivo) {
            r.add("O sistema não tem dados suficientes para ter certeza: na dúvida, não pague nada e confirme por outro canal.");
        }
        return r;
    }
}
