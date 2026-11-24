package com.Presente.A3.Seguranca;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticação ({@code /auth}).
 *
 * <ul>
 *   <li>{@code POST /auth/registro}: cria conta e devolve o token (público);</li>
 *   <li>{@code POST /auth/login}: devolve o token (público);</li>
 *   <li>{@code GET /auth/eu}: dados de quem está logado (exige token).</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 201 com o token; 400 dados inválidos; 409 e-mail ou telefone já cadastrado. */
    @Publico
    @PostMapping("/registro")
    public ResponseEntity<RespostaAuth> registrar(@RequestBody RegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(request));
    }

    /** 200 com o token; 401 credenciais inválidas; 429 excesso de tentativas. */
    @Publico
    @PostMapping("/login")
    public RespostaAuth login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    /** Sem {@code @Publico}: segue a regra padrão e exige token válido. */
    @GetMapping("/eu")
    public RespostaAuth.ClienteResumo eu(HttpServletRequest http) {
        return authService.eu(UsuarioAutenticado.de(http));
    }
}
