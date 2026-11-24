package com.Presente.A3.Seguranca;

/**
 * Nível de acesso exigido por um endpoint.
 *
 * <p>Regra do sistema: <b>tudo que não for marcado exige login</b> (nível
 * {@link #AUTENTICADO}). Para liberar, é preciso marcar explicitamente com
 * {@link Publico}; para restringir, com {@link SomenteAdmin}. Assim, um
 * endpoint novo esquecido nunca fica aberto por engano.</p>
 */
public enum NivelAcesso {
    /** Qualquer pessoa, mesmo sem login. */
    PUBLICO,
    /** Qualquer usuário com token JWT válido. */
    AUTENTICADO,
    /** Apenas usuários com perfil ADMIN. */
    ADMIN
}
