package com.Presente.A3.Analise;

import java.util.Optional;

/**
 * <b>Camada 3 (opcional)</b>: ponto de extensão para um modelo de IA externo.
 *
 * <p>O sistema funciona sem nenhuma implementação desta interface. Quando
 * existe uma (ver {@link AnalisadorIaAnthropic}), ela só é consultada nos
 * casos em que as camadas 1 e 2 ficam em dúvida. Para trocar de provedor
 * basta criar outra classe que implemente esta interface.</p>
 */
public interface AnalisadorIA {

    /** Parecer da IA: nota de 0 a 100 e uma justificativa curta. */
    record Parecer(int pontos, String justificativa) {
    }

    /**
     * @param mensagem    texto recebido
     * @param solicitante identificador de quem pediu (ex.: IP), usado para limitar o uso
     * @return o parecer, ou vazio se a IA estiver indisponível, sem cota ou com erro
     *         (o chamador sempre continua funcionando só com as camadas locais)
     */
    Optional<Parecer> analisar(String mensagem, String solicitante);
}
