package com.Presente.A3.Analise;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitários mínimos de JSON (sem dependências externas) para montar e ler
 * os poucos campos de texto de que as integrações opcionais precisam.
 *
 * <p>Não é um parser completo: lê apenas o valor texto da primeira ocorrência
 * de uma chave. Basta para as respostas simples da IA e da BrasilAPI.</p>
 */
public final class JsonMinimo {

    private JsonMinimo() {
    }

    public static String escapar(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Devolve o valor (texto já "des-escapado") da primeira chave {@code "chave": "..."} do JSON. */
    public static String extrairString(String json, String chave) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(chave) + "\"\\s*:\\s*\"").matcher(json);
        if (!m.find()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = m.end(); i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> sb.append(n); // \" \\ \/
                }
            } else {
                sb.append(c);
            }
        }
        return null; // string não terminada
    }
}
