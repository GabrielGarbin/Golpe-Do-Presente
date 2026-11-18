package com.Presente.A3.Analise;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <b>Camada 1</b> da análise de mensagens: regras e palavras-chave com pesos.
 *
 * <p>Custo zero, resposta instantânea e totalmente explicável: cada regra
 * disparada vira um motivo mostrado ao usuário. É a camada que mais pesa na
 * nota final.</p>
 *
 * <p>Os termos ficam sem acento e em minúsculas, pois o texto é normalizado
 * da mesma forma. A comparação exige que o termo comece no início de uma
 * palavra (assim "taxa" não dispara dentro de "extaxado").</p>
 */
@Component
public class AnalisadorRegras {

    /** Bônus quando aparecem juntos "brinde" e "cobrança": é a assinatura do golpe. */
    static final int BONUS_COMBINACAO = 20;

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

    /** Resultado da camada: pontos (0 a 100), motivos e regras disparadas. */
    public record ResultadoRegras(int pontos, List<String> motivos, Set<String> disparadas) {
    }

    /** @param mensagem texto original recebido (não nulo) */
    public ResultadoRegras analisar(String mensagem) {
        String texto = TextoUtil.normalizar(mensagem);
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

        if (disparadas.contains("BRINDE")
                && (disparadas.contains("COBRANCA") || disparadas.contains("PAGAMENTO"))) {
            pontos += BONUS_COMBINACAO;
            motivos.add("Combinação típica do Golpe do Presente: brinde + cobrança (+"
                    + BONUS_COMBINACAO + ")");
        }
        return new ResultadoRegras(Math.min(100, pontos), motivos, disparadas);
    }

    private static final class Regra {
        private final String id;
        private final String descricao;
        private final int peso;
        private final List<Pattern> padroes = new ArrayList<>();

        private Regra(String id, String descricao, int peso, String... termos) {
            this.id = id;
            this.descricao = descricao;
            this.peso = peso;
            for (String termo : termos) {
                padroes.add(Pattern.compile("(?<![a-z0-9])" + Pattern.quote(termo)));
            }
        }

        private boolean casaCom(String textoNormalizado) {
            for (Pattern p : padroes) {
                if (p.matcher(textoNormalizado).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
