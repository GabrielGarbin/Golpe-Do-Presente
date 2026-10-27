package com.Presente.A3.Dto;

import com.Presente.A3.Modelo.NivelRisco;

import java.util.List;

/**
 * Resposta dos endpoints de prevenção: quão arriscado é o número/mensagem
 * consultado, por quê, e o que a pessoa deve fazer.
 */
public class ResultadoVerificacao {

    private final NivelRisco nivelRisco;
    /** De 0 (nenhum indício) a 100 (todos os indícios). */
    private final int pontuacao;
    /** Explica de onde veio a pontuação (transparência para o usuário). */
    private final List<String> motivos;
    private final List<String> recomendacoes;

    public ResultadoVerificacao(NivelRisco nivelRisco, int pontuacao,
                                List<String> motivos, List<String> recomendacoes) {
        this.nivelRisco = nivelRisco;
        this.pontuacao = pontuacao;
        this.motivos = motivos;
        this.recomendacoes = recomendacoes;
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
}
