package com.Presente.A3.Modelo;

/**
 * Situação de um número/perfil dentro da base colaborativa de denúncias.
 *
 * <p>A mudança de status é automática e depende apenas da quantidade de
 * denúncias feitas por pessoas diferentes (ver {@code GolpistaService}).</p>
 */
public enum StatusGolpista {

    /** Tem ao menos uma denúncia, mas ainda não atingiu o limite de confirmação. */
    SUSPEITO,

    /** Atingiu o limite de denúncias distintas e passa a integrar a blacklist. */
    CONFIRMADO
}
