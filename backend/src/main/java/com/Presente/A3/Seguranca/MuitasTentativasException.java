package com.Presente.A3.Seguranca;

/** Excesso de tentativas de login em pouco tempo (HTTP 429). */
public class MuitasTentativasException extends RuntimeException {

    public MuitasTentativasException(String mensagem) {
        super(mensagem);
    }
}
