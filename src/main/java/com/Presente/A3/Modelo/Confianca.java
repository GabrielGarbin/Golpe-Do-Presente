package com.Presente.A3.Modelo;

/** Quão seguro o sistema está do próprio resultado (independente de ser golpe ou não). */
public enum Confianca {
    /** Camadas de análise concordam e há bastante evidência. */
    ALTA,
    /** Há indícios coerentes, mas com alguma incerteza. */
    MEDIA,
    /** Pouca evidência ou camadas em desacordo: trate como "sem dados suficientes". */
    BAIXA
}
