package com.Presente.A3.Analise;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Controle simples de cota diária: um teto por solicitante e um teto global.
 * Protege a chave de API contra uso excessivo (por engano ou abuso).
 * Os contadores zeram quando o dia muda. Fica em memória, como o resto da base.
 */
public class LimitadorUso {

    private final int limitePorSolicitante;
    private final int limiteGlobal;
    private final Map<String, Integer> usoPorSolicitante = new HashMap<>();
    private int usoGlobal;
    private LocalDate dia = LocalDate.now();

    public LimitadorUso(int limitePorSolicitante, int limiteGlobal) {
        this.limitePorSolicitante = limitePorSolicitante;
        this.limiteGlobal = limiteGlobal;
    }

    /** Reserva uma chamada. @return false se algum dos tetos já foi atingido. */
    public synchronized boolean tentar(String solicitante) {
        LocalDate hoje = LocalDate.now();
        if (!hoje.equals(dia)) {
            dia = hoje;
            usoGlobal = 0;
            usoPorSolicitante.clear();
        }
        int uso = usoPorSolicitante.getOrDefault(solicitante, 0);
        if (usoGlobal >= limiteGlobal || uso >= limitePorSolicitante) {
            return false;
        }
        usoGlobal++;
        usoPorSolicitante.put(solicitante, uso + 1);
        return true;
    }
}
