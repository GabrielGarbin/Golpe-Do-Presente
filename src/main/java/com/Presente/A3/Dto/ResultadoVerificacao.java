package com.Presente.A3.Dto;

import com.Presente.A3.Modelo.Confianca;
import com.Presente.A3.Modelo.NivelRisco;

import java.util.List;

/**
 * Resposta dos endpoints de prevenção: quão arriscado é o número/mensagem
 * consultado, por quê, e o que a pessoa deve fazer.
 *
 * <p>Os campos {@code confianca}, {@code inconclusivo} e {@code camadas} são
 * acréscimos compatíveis: o front-end antigo continua funcionando e o novo
 * pode mostrar o alerta honesto de "sem dados suficientes".</p>
 */
public class ResultadoVerificacao {

    private final NivelRisco nivelRisco;
    /** De 0 (nenhum indício) a 100 (todos os indícios). */
    private final int pontuacao;
    /** Explica de onde veio a pontuação (transparência para o usuário). */
    private final List<String> motivos;
    private final List<String> recomendacoes;
    /** Quão seguro o sistema está do resultado. */
    private final Confianca confianca;
    /** true quando não há dados suficientes para uma conclusão confiável. */
    private final boolean inconclusivo;
    /** Fontes usadas: REGRAS, CLASSIFICADOR_LOCAL, BASE_COLABORATIVA, IA. */
    private final List<String> camadas;

    public ResultadoVerificacao(NivelRisco nivelRisco, int pontuacao,
                                List<String> motivos, List<String> recomendacoes) {
        this(nivelRisco, pontuacao, motivos, recomendacoes, Confianca.MEDIA, false, List.of());
    }

    public ResultadoVerificacao(NivelRisco nivelRisco, int pontuacao,
                                List<String> motivos, List<String> recomendacoes,
                                Confianca confianca, boolean inconclusivo, List<String> camadas) {
        this.nivelRisco = nivelRisco;
        this.pontuacao = pontuacao;
        this.motivos = motivos;
        this.recomendacoes = recomendacoes;
        this.confianca = confianca;
        this.inconclusivo = inconclusivo;
        this.camadas = camadas;
    }

    public NivelRisco getNivelRisco() {
        return nivelRisco;
    }

    public int getPontuacao() {
        return pontuacao;
    }

    public List<String> getMotivos() {
        return motivos;
    }

    public List<String> getRecomendacoes() {
        return recomendacoes;
    }

    public Confianca getConfianca() {
        return confianca;
    }

    public boolean isInconclusivo() {
        return inconclusivo;
    }

    public List<String> getCamadas() {
        return camadas;
    }
}
