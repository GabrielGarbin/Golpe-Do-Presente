package com.Presente.A3.Seguranca;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários da proteção contra força bruta. */
class LimitadorLoginTest {

    private static final class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-11-24T12:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
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
    private final LimitadorLogin limitador = new LimitadorLogin(3, Duration.ofMinutes(15), relogio);

    @Test
    void bloqueiaSomenteAoAtingirOMaximoDeFalhas() {
        limitador.registrarFalha("k");
        limitador.registrarFalha("k");
        assertFalse(limitador.bloqueado("k"));

        limitador.registrarFalha("k");
        assertTrue(limitador.bloqueado("k"));
    }

    @Test
    void cadaChaveTemSuaPropriaContagem() {
        for (int i = 0; i < 3; i++) {
            limitador.registrarFalha("ip1|ana@x.com");
        }
        assertTrue(limitador.bloqueado("ip1|ana@x.com"));
        assertFalse(limitador.bloqueado("ip2|ana@x.com"));
        assertFalse(limitador.bloqueado("ip1|bia@x.com"));
    }

    @Test
    void desbloqueiaQuandoAJanelaTermina() {
        for (int i = 0; i < 3; i++) {
            limitador.registrarFalha("k");
        }
        relogio.avancar(Duration.ofMinutes(14));
        assertTrue(limitador.bloqueado("k"));

        relogio.avancar(Duration.ofMinutes(1));
        assertFalse(limitador.bloqueado("k"));
    }

    @Test
    void loginBemSucedidoZeraAContagem() {
        limitador.registrarFalha("k");
        limitador.registrarFalha("k");
        limitador.limpar("k");
        limitador.registrarFalha("k");

        assertFalse(limitador.bloqueado("k"));
    }
}
