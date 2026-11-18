package com.Presente.A3.Analise;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pré-processamento de texto compartilhado pelas camadas de análise.
 *
 * <p>Padroniza a mensagem (sem acento, minúscula) e troca links, valores em
 * reais e números por marcadores genéricos ({@code urllink}, {@code dinheiro},
 * {@code numero}). Assim o classificador aprende "tem link" e "tem valor em
 * dinheiro", e não o link ou o valor específico de uma mensagem.</p>
 */
public final class TextoUtil {

    private static final Pattern LINK = Pattern.compile(
            "(https?://\\S+|www\\.\\S+|\\b(?:bit\\.ly|tinyurl\\.com|cutt\\.ly|t\\.ly|is\\.gd|rb\\.gy)/\\S*)");
    private static final Pattern DINHEIRO = Pattern.compile("r\\$\\s?\\d+(?:[.,]\\d+)*");
    private static final Pattern NUMERO = Pattern.compile("\\d+");
    private static final Pattern NAO_LETRA = Pattern.compile("[^a-z_]+");

    /** Palavras muito comuns que não ajudam a separar golpe de mensagem normal. */
    private static final Set<String> STOPWORDS = Set.of(
            "de", "a", "o", "e", "da", "do", "em", "um", "uma", "para", "com",
            "por", "os", "as", "no", "na", "que", "se");

    private TextoUtil() {
    }

    /** Remove acentos e coloca em minúsculas. */
    public static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /** Normaliza e troca links, dinheiro e números por marcadores. */
    public static String abstrair(String texto) {
        String t = normalizar(texto);
        t = LINK.matcher(t).replaceAll(" urllink ");
        t = DINHEIRO.matcher(t).replaceAll(" dinheiro ");
        t = NUMERO.matcher(t).replaceAll(" numero ");
        return t;
    }

    /**
     * Gera os atributos usados pelo classificador: palavras isoladas e pares
     * de palavras vizinhas (ex.: {@code taxa_entrega}).
     */
    public static List<String> atributos(String texto) {
        List<String> palavras = new ArrayList<>();
        for (String p : NAO_LETRA.split(abstrair(texto))) {
            if (p.length() >= 2 && !STOPWORDS.contains(p)) {
                palavras.add(p);
            }
        }
        List<String> atributos = new ArrayList<>(palavras);
        for (int i = 0; i + 1 < palavras.size(); i++) {
            atributos.add(palavras.get(i) + "_" + palavras.get(i + 1));
        }
        return atributos;
    }
}
