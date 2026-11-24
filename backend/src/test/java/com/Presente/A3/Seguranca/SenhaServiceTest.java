package com.Presente.A3.Seguranca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Testes unitários do hash de senhas (PBKDF2 com sal). */
class SenhaServiceTest {

    private final SenhaService senhas = new SenhaService(1000);

    @Test
    void senhaCertaConfereESenhaErradaNao() {
        String hash = senhas.gerarHash("MinhaSenha123");

        assertTrue(senhas.confere("MinhaSenha123", hash));
        assertFalse(senhas.confere("minhasenha123", hash));
        assertFalse(senhas.confere("", hash));
    }

    @Test
    void oHashNuncaContemASenha() {
        assertFalse(senhas.gerarHash("MinhaSenha123").contains("MinhaSenha123"));
    }

    @Test
    void mesmaSenhaGeraHashesDiferentesPorCausaDoSal() {
        String h1 = senhas.gerarHash("MinhaSenha123");
        String h2 = senhas.gerarHash("MinhaSenha123");

        assertNotEquals(h1, h2);
        assertTrue(senhas.confere("MinhaSenha123", h1));
        assertTrue(senhas.confere("MinhaSenha123", h2));
    }

    @Test
    void hashMalformadoOuNuloNaoConfere() {
        String hash = senhas.gerarHash("x1234567");

        assertFalse(senhas.confere("x1234567", "lixo"));
        assertFalse(senhas.confere("x1234567", "pbkdf2-sha256$a$b$c"));
        assertFalse(senhas.confere(null, hash));
        assertFalse(senhas.confere("x1234567", null));
    }

    @Test
    void naoAceitaCustoBaixoDemais() {
        assertThrows(IllegalArgumentException.class, () -> new SenhaService(10));
    }
}
