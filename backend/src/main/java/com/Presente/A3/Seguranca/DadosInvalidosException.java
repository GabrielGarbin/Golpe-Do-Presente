package com.Presente.A3.Seguranca;

/** Dados de cadastro/login inválidos (HTTP 400). */
public class DadosInvalidosException extends RuntimeException {

    public DadosInvalidosException(String mensagem) {
        super(mensagem);
    }
}
