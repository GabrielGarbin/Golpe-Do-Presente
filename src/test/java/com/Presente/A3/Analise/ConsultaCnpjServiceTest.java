package com.Presente.A3.Analise;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Testes unitários da validação de CNPJ e do leitor de JSON mínimo (sem rede). */
class ConsultaCnpjServiceTest {

    @Test
    void aceitaCnpjValidoComOuSemMascara() {
        assertEquals("19131243000197", ConsultaCnpjService.normalizar("19.131.243/0001-97"));
        assertEquals("19131243000197", ConsultaCnpjService.normalizar("19131243000197"));
    }

    @Test
    void rejeitaCnpjInvalido() {
        assertThrows(IllegalArgumentException.class, () -> ConsultaCnpjService.normalizar("123"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaCnpjService.normalizar("11111111111111"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaCnpjService.normalizar("11222333000182"));
        assertThrows(IllegalArgumentException.class, () -> ConsultaCnpjService.normalizar(" "));
    }

    @Test
    void leituraDeJsonEscapaEExtrai() {
        assertEquals("a\\\"b\\n", JsonMinimo.escapar("a\"b\n"));
        assertEquals("ATIVA", JsonMinimo.extrairString("{\"situacao\":2,\"descricao_situacao_cadastral\":\"ATIVA\"}",
                "descricao_situacao_cadastral"));
        assertNull(JsonMinimo.extrairString("{\"x\":1}", "descricao_situacao_cadastral"));
    }

    @Test
    void respostaForaDoFormatoEhIgnorada() {
        assertNull(ConsultaCnpjService.interpretar("{\"message\":\"CNPJ não encontrado\"}"));
    }
}
