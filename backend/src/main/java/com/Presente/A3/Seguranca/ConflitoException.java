package com.Presente.A3.Seguranca;

/** Conflito com dados já existentes, como e-mail repetido (HTTP 409). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
