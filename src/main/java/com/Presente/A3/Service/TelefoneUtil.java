package com.Presente.A3.Service;

/**
 * Utilitário para padronizar telefones brasileiros.
 *
 * <p>O telefone é a chave da base colaborativa. Sem padronização,
 * "(11) 98765-4321", "+55 11 98765-4321" e "11987654321" seriam tratados
 * como números diferentes e o golpista escaparia da contagem de denúncias.</p>
 */
public final class TelefoneUtil {

    private TelefoneUtil() {
    }

    /**
     * Remove máscara e código do país, devolvendo só DDD + número.
     *
     * @param bruto telefone digitado pelo usuário, com ou sem máscara
     * @return 10 dígitos (fixo) ou 11 dígitos (celular), ex.: "11987654321"
     * @throws IllegalArgumentException se nulo ou fora do formato brasileiro
     */
    public static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("Telefone é obrigatório.");
        }
        String digitos = bruto.replaceAll("\\D", "");

        // Remove o código do país (+55) quando presente: 55 + (10 ou 11 dígitos).
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() != 10 && digitos.length() != 11) {
            throw new IllegalArgumentException(
                    "Telefone inválido: informe DDD + número (10 ou 11 dígitos).");
        }
        return digitos;
    }
}
