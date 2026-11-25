package com.Presente.A3.Seguranca;

import com.Presente.A3.Service.TelefoneUtil;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regras de validação de cadastro, usadas tanto no registro público quanto
 * no cadastro e edição feitos pelo administrador (as regras são as mesmas).
 */
public final class ValidacaoCadastro {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    public static final int SENHA_MIN = 8;
    public static final int SENHA_MAX = 128;

    private ValidacaoCadastro() {
    }

    /** @return o nome sem espaços nas pontas */
    public static String nome(String nome) {
        String n = nome == null ? "" : nome.trim();
        if (n.length() < 2 || n.length() > 100) {
            throw new DadosInvalidosException("Informe o nome (2 a 100 caracteres).");
        }
        return n;
    }

    /** @return o e-mail em minúsculas e sem espaços */
    public static String email(String email) {
        String e = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 200 || !EMAIL.matcher(e).matches()) {
            throw new DadosInvalidosException("E-mail inválido.");
        }
        return e;
    }

    /** @return o telefone só com dígitos (DDD + número) */
    public static String telefone(String telefone) {
        try {
            return TelefoneUtil.normalizar(telefone);
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException(e.getMessage());
        }
    }

    public static String senha(String senha) {
        if (senha == null || senha.length() < SENHA_MIN || senha.length() > SENHA_MAX) {
            throw new DadosInvalidosException(
                    "A senha deve ter entre " + SENHA_MIN + " e " + SENHA_MAX + " caracteres.");
        }
        return senha;
    }
}
