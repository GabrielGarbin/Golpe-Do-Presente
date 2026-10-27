package com.Presente.A3.Modelo;

/** Classificação de risco devolvida pelos endpoints de prevenção. */
public enum NivelRisco {
    /** Nenhum sinal forte encontrado (isso NÃO garante que seja seguro). */
    BAIXO,
    /** Há indícios: vale desconfiar e confirmar por outro canal. */
    MEDIO,
    /** Forte probabilidade de ser o Golpe do Presente. */
    ALTO
}
