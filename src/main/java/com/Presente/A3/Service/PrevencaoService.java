package com.Presente.A3.Service;

import com.Presente.A3.Analise.AnaliseMensagemService;
import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Modelo.Confianca;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.NivelRisco;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * <p><b>Limitações assumidas:</b> a análise de texto combina regras e um
 * classificador treinado com poucos exemplos, e pode errar em ambos os
 * sentidos. Um resultado BAIXO nunca garante segurança; por isso toda
 * resposta traz recomendações e, quando há pouca evidência, o resultado é
 * marcado como inconclusivo.</p>
 */
@Service
public class PrevencaoService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GolpistaService golpistaService;
    private final AnaliseMensagemService analiseMensagem;

    public PrevencaoService(GolpistaService golpistaService, AnaliseMensagemService analiseMensagem) {
        this.golpistaService = golpistaService;
        this.analiseMensagem = analiseMensagem;
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
            motivos.add("Sem dados suficientes: a base ainda não tem histórico deste número. "
                    + "Isso NÃO significa que ele seja seguro.");
            List<String> rec = recomendacoes(NivelRisco.BAIXO);
            rec.add("Se a mensagem recebida for suspeita, cole o texto na análise de mensagem para uma segunda avaliação.");
            return new ResultadoVerificacao(NivelRisco.BAIXO, 0, motivos, rec,
                    Confianca.BAIXA, true, List.of("BASE_COLABORATIVA"));
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
        return new ResultadoVerificacao(nivel, pontuacao, motivos, recomendacoes(nivel),
                nivel == NivelRisco.ALTO ? Confianca.ALTA : Confianca.MEDIA, false,
                List.of("BASE_COLABORATIVA"));
    }

    // ------------------------------------------------------------------
    // 2) Análise de mensagem
    // ------------------------------------------------------------------

    /**
     * Analisa o texto de uma mensagem recebida e estima o risco de ser o
     * Golpe do Presente, combinando regras, classificador local, base
     * colaborativa e, se configurada, uma IA externa. Os detalhes de cada
     * camada estão em {@link AnaliseMensagemService}.
     *
     * @param mensagem          texto recebido (obrigatório)
     * @param telefoneRemetente número de quem enviou (opcional)
     * @throws IllegalArgumentException mensagem vazia ou telefone inválido
     */
    public ResultadoVerificacao analisarMensagem(String mensagem, String telefoneRemetente) {
        return analisarMensagem(mensagem, telefoneRemetente, "anonimo");
    }

    /** Igual ao anterior, identificando quem pediu (ex.: IP) para limitar o uso da IA. */
    public ResultadoVerificacao analisarMensagem(String mensagem, String telefoneRemetente, String solicitante) {
        return analiseMensagem.analisar(mensagem, telefoneRemetente, solicitante);
    }

    /** @return true se há uma IA externa configurada */
    public boolean iaDisponivel() {
        return analiseMensagem.iaDisponivel();
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
}
