package com.Presente.A3.Service;

import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.NivelRisco;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Camada de <b>prevenção</b>: ajuda a pessoa a desconfiar ANTES de o
 * entregador chegar (e, portanto, antes de qualquer cobrança).
 *
 * <p>Oferece três ferramentas:</p>
 * <ol>
 *   <li>{@link #verificarTelefone(String)}: consulta um número na base colaborativa;</li>
 *   <li>{@link #analisarMensagem(String, String)}: procura padrões típicos do
 *       Golpe do Presente no texto recebido;</li>
 *   <li>{@link #checklistEntrega()}: orientações para antes, durante e depois.</li>
 * </ol>
 *
 * <p><b>Limitações assumidas:</b> a análise de texto é uma heurística simples
 * (palavras-chave com pesos) e pode errar em ambos os sentidos. Um resultado
 * BAIXO nunca garante segurança; por isso toda resposta traz recomendações.
 * Como evolução, este método pode ser substituído por um modelo de
 * classificação de texto sem alterar os endpoints.</p>
 */
@Service
public class PrevencaoService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Pontuação a partir da qual o risco é considerado ALTO. */
    private static final int LIMIAR_ALTO = 60;
    /** Pontuação a partir da qual o risco é considerado MEDIO. */
    private static final int LIMIAR_MEDIO = 30;
    /** Bônus quando aparecem juntos "brinde" e "cobrança": é a assinatura do golpe. */
    private static final int BONUS_COMBINACAO = 20;
    private static final int PESO_REMETENTE_CONFIRMADO = 50;
    private static final int PESO_REMETENTE_SUSPEITO = 25;

    /**
     * Regras de detecção. Os termos estão sem acento e em minúsculas, pois o
     * texto da mensagem é normalizado da mesma forma antes da comparação.
     */
    private static final List<Regra> REGRAS = List.of(
            new Regra("BRINDE", "Promete brinde, prêmio ou presente gratuito", 25,
                    "voce ganhou", "ganhou um", "sorteado", "contemplado", "brinde",
                    "presente gratis", "premio"),
            new Regra("ENTREGADOR", "Diz que um entregador está a caminho", 20,
                    "entregador", "motoboy", "a caminho", "entrega do seu", "entregar seu"),
            new Regra("COBRANCA", "Pede taxa ou frete para entregar o brinde", 30,
                    "taxa de entrega", "taxa", "frete", "valor da entrega", "custo de envio"),
            new Regra("PAGAMENTO", "Menciona pagamento por maquininha ou cartão", 15,
                    "maquininha", "cartao", "debito", "credito"),
            new Regra("URGENCIA", "Cria urgência para você decidir rápido", 10,
                    "urgente", "agora", "hoje", "imediatamente", "ultimas horas", "nao perca"),
            new Regra("LINK", "Contém link ou pede para clicar", 15,
                    "http", "www.", "bit.ly", "clique", "acesse o link"),
            new Regra("DADOS", "Pede confirmação de dados pessoais", 15,
                    "confirme seu endereco", "confirmar seu endereco", "seu cpf",
                    "confirme seus dados", "dados do cartao"));

    private final GolpistaService golpistaService;

    public PrevencaoService(GolpistaService golpistaService) {
        this.golpistaService = golpistaService;
    }

    // ------------------------------------------------------------------
    // 1) Consulta de telefone
    // ------------------------------------------------------------------

    /**
     * Consulta um número na base colaborativa.
     *
     * <ul>
     *   <li>Sem denúncias: risco BAIXO (com aviso de que isso não garante segurança);</li>
     *   <li>Com denúncias abaixo do limite: risco MEDIO;</li>
     *   <li>Confirmado (consta na blacklist): risco ALTO.</li>
     * </ul>
     *
     * @throws IllegalArgumentException se o telefone for inválido
     */
    public ResultadoVerificacao verificarTelefone(String telefone) {
        Optional<Golpista> encontrado = golpistaService.findByTelefone(telefone);
        List<String> motivos = new ArrayList<>();

        if (encontrado.isEmpty()) {
            motivos.add("Nenhuma denúncia registrada para este número na base colaborativa.");
            return new ResultadoVerificacao(NivelRisco.BAIXO, 0, motivos,
                    recomendacoes(NivelRisco.BAIXO));
        }

        Golpista g = encontrado.get();
        int limite = golpistaService.getLimiteConfirmacao();
        int pontuacao = Math.min(100, g.getTotalDenuncias() * 100 / limite);

        motivos.add("Número denunciado por " + g.getTotalDenuncias()
                + " pessoa(s); primeira denúncia em " + g.getDataPrimeiraDenuncia().format(DATA) + ".");

        NivelRisco nivel = NivelRisco.MEDIO;
        if (g.getStatus() == StatusGolpista.CONFIRMADO) {
            nivel = NivelRisco.ALTO;
            motivos.add("O número atingiu o limite de " + limite + " denúncias e consta na blacklist.");
        }
        return new ResultadoVerificacao(nivel, pontuacao, motivos, recomendacoes(nivel));
    }

    // ------------------------------------------------------------------
    // 2) Análise de mensagem
    // ------------------------------------------------------------------

    /**
     * Analisa o texto de uma mensagem recebida e estima o risco de ser o
     * Golpe do Presente.
     *
     * <p>Cada regra disparada soma pontos (máx. 100). Se brinde e cobrança
     * aparecem juntos, soma-se um bônus. Se o remetente informado estiver na
     * base colaborativa, soma-se ainda mais e, quando CONFIRMADO, o risco é
     * sempre ALTO.</p>
     *
     * @param mensagem          texto recebido (obrigatório)
     * @param telefoneRemetente número de quem enviou (opcional)
     * @throws IllegalArgumentException mensagem vazia ou telefone inválido
     */
    public ResultadoVerificacao analisarMensagem(String mensagem, String telefoneRemetente) {
        if (mensagem == null || mensagem.isBlank()) {
            throw new IllegalArgumentException("A mensagem a ser analisada é obrigatória.");
        }

        String texto = normalizarTexto(mensagem);
        int pontos = 0;
        List<String> motivos = new ArrayList<>();
        Set<String> disparadas = new HashSet<>();

        for (Regra regra : REGRAS) {
            if (regra.casaCom(texto)) {
                pontos += regra.peso;
                disparadas.add(regra.id);
                motivos.add(regra.descricao + " (+" + regra.peso + ")");
            }
        }

        // Assinatura do golpe: prometer um brinde e, ao mesmo tempo, cobrar algo.
        if (disparadas.contains("BRINDE")
                && (disparadas.contains("COBRANCA") || disparadas.contains("PAGAMENTO"))) {
            pontos += BONUS_COMBINACAO;
            motivos.add("Combinação típica do Golpe do Presente: brinde + cobrança (+"
                    + BONUS_COMBINACAO + ")");
        }

        // Cruza com a base colaborativa, se o remetente foi informado.
        boolean remetenteConfirmado = false;
        if (telefoneRemetente != null && !telefoneRemetente.isBlank()) {
            Optional<Golpista> remetente = golpistaService.findByTelefone(telefoneRemetente);
            if (remetente.isPresent()) {
                if (remetente.get().getStatus() == StatusGolpista.CONFIRMADO) {
                    remetenteConfirmado = true;
                    pontos += PESO_REMETENTE_CONFIRMADO;
                    motivos.add("O remetente consta na blacklist colaborativa (+"
                            + PESO_REMETENTE_CONFIRMADO + ")");
                } else {
                    pontos += PESO_REMETENTE_SUSPEITO;
                    motivos.add("O remetente já foi denunciado, mas ainda não confirmado (+"
                            + PESO_REMETENTE_SUSPEITO + ")");
                }
            }
        }

        if (remetenteConfirmado) {
            pontos = Math.max(pontos, LIMIAR_ALTO); // coerência entre nota e nível
        }
        pontos = Math.min(100, pontos);

        NivelRisco nivel;
        if (remetenteConfirmado || pontos >= LIMIAR_ALTO) {
            nivel = NivelRisco.ALTO;
        } else if (pontos >= LIMIAR_MEDIO) {
            nivel = NivelRisco.MEDIO;
        } else {
            nivel = NivelRisco.BAIXO;
        }

        if (motivos.isEmpty()) {
            motivos.add("Nenhum padrão típico do golpe foi identificado no texto.");
        }
        return new ResultadoVerificacao(nivel, pontos, motivos, recomendacoes(nivel));
    }

    // ------------------------------------------------------------------
    // 3) Checklist educativo
    // ------------------------------------------------------------------

    /**
     * Orientações práticas divididas em três momentos. Conteúdo fixo
     * (educativo), mantido em um único lugar para facilitar a revisão.
     *
     * @return mapa com as chaves "antes", "na_hora_da_entrega" e "se_ja_pagou"
     */
    public Map<String, List<String>> checklistEntrega() {
        Map<String, List<String>> checklist = new LinkedHashMap<>();
        checklist.put("antes", List.of(
                "Desconfie de brindes que você não pediu, principalmente se cobrarem qualquer valor para entregar.",
                "Confirme a promoção direto com a loja, pelo site ou telefone que você mesmo procurou (nunca o número da mensagem).",
                "Consulte o número do remetente neste sistema antes de responder.",
                "Lembre: golpistas usam dados vazados (nome, endereço, aniversário). Saberem seus dados não prova que a mensagem é legítima."));
        checklist.put("na_hora_da_entrega", List.of(
                "Não pague taxa alguma para receber um presente: recuse a entrega.",
                "Se mesmo assim for pagar, exija ver o visor da maquininha e confira o valor antes de digitar a senha. Visor coberto ou 'com defeito' é sinal de alerta.",
                "Desconfie de quem recusa dinheiro ou Pix e insiste em passar o cartão.",
                "Não aceite 'refazer' a cobrança alegando erro e não tire selfie de 'confirmação de recebimento'."));
        checklist.put("se_ja_pagou", List.of(
                "Ligue imediatamente para o banco (número no verso do cartão), informe a cobrança indevida e peça o bloqueio do cartão e a contestação da compra.",
                "Registre um boletim de ocorrência e guarde mensagens, prints e o número do remetente.",
                "Denuncie o número neste sistema para alertar outras pessoas."));
        return checklist;
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Recomendações padrão conforme o nível de risco. */
    private List<String> recomendacoes(NivelRisco nivel) {
        List<String> r = new ArrayList<>();
        switch (nivel) {
            case ALTO:
                r.add("Não pague nada, não informe dados de cartão e não clique em links.");
                r.add("Confirme com a loja pelos canais oficiais, usando um contato que você mesmo procurou.");
                r.add("Denuncie o número para proteger outras pessoas.");
                break;
            case MEDIO:
                r.add("Desconfie: confirme a informação com a loja por um canal oficial antes de qualquer pagamento.");
                r.add("Lembre-se de que nenhuma loja legítima cobra para entregar um brinde.");
                break;
            default:
                r.add("Nenhum sinal forte encontrado, mas a ausência de alertas não garante que seja legítimo.");
                r.add("Em caso de dúvida, confirme com a loja por um canal oficial.");
        }
        return r;
    }

    /** Remove acentos e padroniza caixa, para comparar texto de forma tolerante. */
    private static String normalizarTexto(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /** Regra de detecção: identificador, descrição, peso e termos que a ativam. */
    private static final class Regra {
        private final String id;
        private final String descricao;
        private final int peso;
        private final String[] termos;

        private Regra(String id, String descricao, int peso, String... termos) {
            this.id = id;
            this.descricao = descricao;
            this.peso = peso;
            this.termos = termos;
        }

        /** @return true se o texto (já normalizado) contém algum dos termos */
        private boolean casaCom(String textoNormalizado) {
            for (String termo : termos) {
                if (textoNormalizado.contains(termo)) {
                    return true;
                }
            }
            return false;
        }
    }
}
