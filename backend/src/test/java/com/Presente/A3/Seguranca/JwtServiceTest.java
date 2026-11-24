package com.Presente.A3.Seguranca;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários do JWT: emissão, validação, adulteração e expiração (sem Spring, rápidos). */
class JwtServiceTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-trinta-e-dois-caracteres";

    /** Relógio que o teste controla, para simular a passagem do tempo sem esperar. */
    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-11-24T12:00:00Z");

        void avancarSegundos(long s) {
            agora = agora.plusSeconds(s);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final JwtService jwt = new JwtService(SEGREDO, 60, relogio);

    @Test
    void tokenValidoDevolveQuemEEOPerfil() {
        UsuarioAutenticado u = jwt.validar(jwt.gerar(42L, Perfil.USUARIO)).orElseThrow();

        assertEquals(42L, u.clienteId());
        assertEquals(Perfil.USUARIO, u.perfil());
        assertFalse(u.ehAdmin());
        assertTrue(jwt.validar(jwt.gerar(1L, Perfil.ADMIN)).orElseThrow().ehAdmin());
    }

    @Test
    void trocarOPerfilNoPayloadInvalidaAAssinatura() {
        String[] partes = jwt.gerar(42L, Perfil.USUARIO).split("\\.");
        String payloadAdmin = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"42\",\"perfil\":\"ADMIN\",\"iat\":1,\"exp\":99999999999}".getBytes(StandardCharsets.UTF_8));

        assertTrue(jwt.validar(partes[0] + "." + payloadAdmin + "." + partes[2]).isEmpty(),
                "escalada de privilégio não pode funcionar");
    }

    @Test
    void assinaturaAlteradaOuDeOutroSegredoEhRecusada() {
        String token = jwt.gerar(42L, Perfil.USUARIO);
        String adulterado = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        assertTrue(jwt.validar(adulterado).isEmpty());
        assertTrue(new JwtService("outro-segredo-com-mais-de-trinta-e-dois-chars!!", 60, relogio).validar(token).isEmpty());
    }

    @Test
    void algoritmoNoneEhRecusado() {
        String[] partes = jwt.gerar(42L, Perfil.USUARIO).split("\\.");
        String cabecalhoNone = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

        assertTrue(jwt.validar(cabecalhoNone + "." + partes[1] + ".").isEmpty());
        assertTrue(jwt.validar(cabecalhoNone + "." + partes[1] + "." + partes[2]).isEmpty());
    }

    @Test
    void tokensMalformadosSaoRecusados() {
        assertTrue(jwt.validar(null).isEmpty());
        assertTrue(jwt.validar("").isEmpty());
        assertTrue(jwt.validar("abc").isEmpty());
        assertTrue(jwt.validar("a.b.c.d").isEmpty());
        assertTrue(jwt.validar("a.b.c").isEmpty());
    }

    @Test
    void tokenExpiraNoMomentoCerto() {
        String token = jwt.gerar(42L, Perfil.USUARIO); // validade: 60 minutos

        relogio.avancarSegundos(3599);
        assertTrue(jwt.validar(token).isPresent(), "1 segundo antes de expirar");

        relogio.avancarSegundos(1);
        assertTrue(jwt.validar(token).isEmpty(), "no instante de expiração");
    }

    @Test
    void segredoCurtoImpedeAInicializacao() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("curto", 60, relogio));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SEGREDO, 0, relogio));
    }

    @Test
    void semSegredoUsaUmAleatorioQueNaoValidaTokensDeOutraInstancia() {
        JwtService aleatorio = new JwtService("", 60, relogio);

        assertTrue(aleatorio.validar(aleatorio.gerar(1L, Perfil.USUARIO)).isPresent());
        assertTrue(aleatorio.validar(jwt.gerar(1L, Perfil.USUARIO)).isEmpty());
    }
}
