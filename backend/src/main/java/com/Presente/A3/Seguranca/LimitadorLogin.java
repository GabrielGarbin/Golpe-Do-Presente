package com.Presente.A3.Seguranca;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra tentativa de adivinhar senhas por força bruta.
 *
 * <p>Conta falhas de login por chave (IP + e-mail). Ao atingir o máximo dentro
 * da janela de tempo, novas tentativas são bloqueadas até a janela terminar
 * (resposta HTTP 429). Um login bem-sucedido zera a contagem.</p>
 *
 * <p>Limitações assumidas: a contagem fica em memória (zera ao reiniciar e não
 * é compartilhada entre várias instâncias) e atrás de um proxy todos os
 * acessos podem parecer vir do mesmo IP. Para produção em escala, usar um
 * armazenamento compartilhado (ex.: Redis).</p>
 */
@Component
public class LimitadorLogin {

    /** Evita crescimento sem limite do mapa caso alguém varie as chaves de propósito. */
    private static final int LIMITE_PARA_LIMPEZA = 10_000;

    private record Registro(int falhas, Instant inicio) {
    }

    private final int maximoFalhas;
    private final Duration janela;
    private final Clock relogio;
    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    @Autowired
    public LimitadorLogin(@Value("${app.auth.login-max-falhas:5}") int maximoFalhas,
                          @Value("${app.auth.login-janela-minutos:15}") long janelaMinutos) {
        this(maximoFalhas, Duration.ofMinutes(janelaMinutos), Clock.systemUTC());
    }

    /** Construtor com relógio injetável, para testar sem esperar minutos de verdade. */
    public LimitadorLogin(int maximoFalhas, Duration janela, Clock relogio) {
        this.maximoFalhas = maximoFalhas;
        this.janela = janela;
        this.relogio = relogio;
    }

    /** @return true se esta chave já atingiu o máximo de falhas dentro da janela */
    public boolean bloqueado(String chave) {
        Registro r = registros.get(chave);
        if (r == null) {
            return false;
        }
        if (expirou(r)) {
            registros.remove(chave, r);
            return false;
        }
        return r.falhas() >= maximoFalhas;
    }

    /** Registra uma falha de login para a chave. */
    public void registrarFalha(String chave) {
        if (registros.size() > LIMITE_PARA_LIMPEZA) {
            registros.values().removeIf(this::expirou);
        }
        registros.compute(chave, (k, atual) -> (atual == null || expirou(atual))
                ? new Registro(1, relogio.instant())
                : new Registro(atual.falhas() + 1, atual.inicio()));
    }

    /** Zera a contagem (login bem-sucedido). */
    public void limpar(String chave) {
        registros.remove(chave);
    }

    private boolean expirou(Registro r) {
        return !relogio.instant().isBefore(r.inicio().plus(janela));
    }
}
