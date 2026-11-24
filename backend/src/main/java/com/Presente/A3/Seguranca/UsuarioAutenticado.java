package com.Presente.A3.Seguranca;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Identidade extraída de um token JWT válido: quem está fazendo a requisição.
 *
 * <p>É assim que o servidor sabe QUEM é o denunciante, sem confiar em
 * nenhum {@code clienteId} enviado no corpo da requisição (que poderia ser
 * forjado por qualquer pessoa).</p>
 *
 * @param clienteId id do cliente dono do token
 * @param perfil    perfil no momento do login
 * @param expiraEm  instante de expiração, em segundos desde 1970 (epoch)
 */
public record UsuarioAutenticado(Long clienteId, Perfil perfil, long expiraEm) {

    /** Nome do atributo da requisição onde o interceptor guarda o usuário. */
    public static final String ATRIBUTO = "usuarioAutenticado";

    public boolean ehAdmin() {
        return perfil == Perfil.ADMIN;
    }

    /**
     * Recupera o usuário que o interceptor autenticou nesta requisição.
     *
     * @throws AutenticacaoException se o endpoint foi chamado sem login
     *                               (não deveria ocorrer em endpoints protegidos)
     */
    public static UsuarioAutenticado de(HttpServletRequest request) {
        Object u = request.getAttribute(ATRIBUTO);
        if (u instanceof UsuarioAutenticado usuario) {
            return usuario;
        }
        throw new AutenticacaoException("Autenticação necessária.");
    }
}
