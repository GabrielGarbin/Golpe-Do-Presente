package com.Presente.A3.Seguranca;

import com.Presente.A3.Analise.JsonMinimo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Porteiro da API: roda ANTES de cada endpoint e decide se a requisição entra.
 *
 * <pre>
 *   requisição → lê "Authorization: Bearer ..." → valida o JWT
 *              → compara com o nível exigido pelo endpoint
 *                    PUBLICO     → entra sempre
 *                    AUTENTICADO → exige token válido (senão 401)
 *                    ADMIN       → exige token de ADMIN (senão 403)
 * </pre>
 *
 * <p>O nível vem das anotações {@link Publico} e {@link SomenteAdmin}. A anotação
 * do método vale mais que a da classe. <b>Sem anotação, o endpoint exige login</b>
 * (seguro por padrão).</p>
 *
 * <p>Usa-se um interceptor do Spring MVC (e não um filtro de servlet) porque ele
 * roda depois do tratamento de CORS: assim a resposta 401 também carrega os
 * cabeçalhos de CORS e o navegador consegue ler o erro.</p>
 */
@Component
public class AutenticacaoInterceptor implements HandlerInterceptor {

    private static final String PREFIXO = "Bearer ";

    private final JwtService jwt;

    public AutenticacaoInterceptor(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // Só controla endpoints; arquivos estáticos e a pré-verificação de CORS passam direto.
        if (!(handler instanceof HandlerMethod metodo) || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Optional<UsuarioAutenticado> usuario = extrairUsuario(request);
        usuario.ifPresent(u -> request.setAttribute(UsuarioAutenticado.ATRIBUTO, u));

        NivelAcesso exigido = nivelExigido(metodo);
        if (exigido == NivelAcesso.PUBLICO) {
            return true;
        }
        if (usuario.isEmpty()) {
            response.setHeader("WWW-Authenticate", "Bearer");
            negar(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Autenticação necessária: envie o cabeçalho Authorization com o token (Bearer).");
            return false;
        }
        if (exigido == NivelAcesso.ADMIN && !usuario.get().ehAdmin()) {
            negar(response, HttpServletResponse.SC_FORBIDDEN, "Acesso restrito a administradores.");
            return false;
        }
        return true;
    }

    /** Anotação do método vence a da classe; sem nenhuma, exige login. */
    static NivelAcesso nivelExigido(HandlerMethod metodo) {
        if (metodo.getMethodAnnotation(Publico.class) != null) {
            return NivelAcesso.PUBLICO;
        }
        if (metodo.getMethodAnnotation(SomenteAdmin.class) != null) {
            return NivelAcesso.ADMIN;
        }
        Class<?> classe = metodo.getBeanType();
        if (classe.getAnnotation(Publico.class) != null) {
            return NivelAcesso.PUBLICO;
        }
        if (classe.getAnnotation(SomenteAdmin.class) != null) {
            return NivelAcesso.ADMIN;
        }
        return NivelAcesso.AUTENTICADO;
    }

    private Optional<UsuarioAutenticado> extrairUsuario(HttpServletRequest request) {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho == null || !cabecalho.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
            return Optional.empty();
        }
        return jwt.validar(cabecalho.substring(PREFIXO.length()).trim());
    }

    private void negar(HttpServletResponse response, int status, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(
                ("{\"erro\":\"" + JsonMinimo.escapar(mensagem) + "\"}").getBytes(StandardCharsets.UTF_8));
    }
}
