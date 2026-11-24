package com.Presente.A3.Seguranca;

/** Falha de autenticação: credenciais ou token inválidos (HTTP 401). */
public class AutenticacaoException extends RuntimeException {

    public AutenticacaoException(String mensagem) {
        super(mensagem);
    }
}
