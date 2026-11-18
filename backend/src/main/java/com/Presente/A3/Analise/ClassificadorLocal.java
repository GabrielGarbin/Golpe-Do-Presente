package com.Presente.A3.Analise;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Camada 2</b>: classificador de texto local (Naive Bayes multinomial),
 * sem biblioteca externa e sem custo por uso.
 *
 * <p>Aprende com o arquivo {@code analise/corpus.txt} ao iniciar e devolve a
 * semelhança da mensagem com os exemplos de golpe (0 a 100). Complementa as
 * regras: reconhece variações de texto que nenhuma palavra-chave isolada
 * pegaria.</p>
 *
 * <p><b>Limites assumidos:</b> o corpus inicial é pequeno e sintético, então
 * a nota é uma estimativa, não uma garantia. Use {@link #aprender} para
 * acrescentar exemplos reais (ex.: descrições das denúncias).</p>
 */
@Component
public class ClassificadorLocal {

    private static final String RECURSO = "/analise/corpus.txt";
    /** Quantidade mínima de atributos conhecidos para confiar na nota. */
    static final int MIN_ATRIBUTOS_CONHECIDOS = 3;

    /**
     * Inclinação da curva que converte a diferença de log-verossimilhança em
     * nota de 0 a 100. Valor ajustado à mão em mensagens de teste: acima de
     * ~1,5 a nota "satura" em 0 ou 100 e perde utilidade como gradação.
     */
    private static final double INCLINACAO = 0.8;

    private final Map<String, int[]> contagem = new HashMap<>(); // [golpe, legítima]
    private final Set<String> vocabulario = new HashSet<>();
    private int totalGolpe;
    private int totalLegitima;

    /** Resultado do classificador: nota 0 a 100 e quantos atributos eram conhecidos. */
    public record Resultado(int pontos, int atributosConhecidos) {
        public boolean temBase() {
            return atributosConhecidos >= MIN_ATRIBUTOS_CONHECIDOS;
        }
    }

    public ClassificadorLocal() {
        try (InputStream in = ClassificadorLocal.class.getResourceAsStream(RECURSO)) {
            if (in == null) {
                throw new IllegalStateException("Corpus não encontrado: " + RECURSO);
            }
            BufferedReader leitor = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String linha;
            while ((linha = leitor.readLine()) != null) {
                if (linha.isBlank() || linha.startsWith("#") || linha.length() < 3 || linha.charAt(1) != '|') {
                    continue;
                }
                aprender(linha.substring(2), linha.charAt(0) == 'G');
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o corpus de treino.", e);
        }
    }

    /** Acrescenta um exemplo ao modelo (golpe = true, mensagem legítima = false). */
    public synchronized void aprender(String texto, boolean golpe) {
        for (String atributo : TextoUtil.atributos(texto)) {
            int[] c = contagem.computeIfAbsent(atributo, k -> new int[2]);
            c[golpe ? 0 : 1]++;
            vocabulario.add(atributo);
            if (golpe) {
                totalGolpe++;
            } else {
                totalLegitima++;
            }
        }
    }

    /** Estima de 0 a 100 o quanto o texto se parece com golpe. */
    public synchronized Resultado classificar(String texto) {
        List<String> atributos = TextoUtil.atributos(texto);
        double logGolpe = 0;
        double logLegitima = 0;
        int conhecidos = 0;
        int v = Math.max(1, vocabulario.size());
        for (String a : atributos) {
            int[] c = contagem.get(a);
            if (c == null) {
                continue; // atributo nunca visto: ignora
            }
            conhecidos++;
            logGolpe += Math.log((c[0] + 1.0) / (totalGolpe + v));
            logLegitima += Math.log((c[1] + 1.0) / (totalLegitima + v));
        }
        if (conhecidos == 0) {
            return new Resultado(50, 0);
        }
        // Naive Bayes é excessivamente confiante em textos longos; suavizamos
        // dividindo a diferença pela raiz da quantidade de atributos.
        double logOdds = (logGolpe - logLegitima) / Math.sqrt(conhecidos);
        double p = 1.0 / (1.0 + Math.exp(-logOdds * INCLINACAO));
        return new Resultado((int) Math.round(p * 100), conhecidos);
    }
}
