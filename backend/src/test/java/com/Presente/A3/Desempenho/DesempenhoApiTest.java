package com.Presente.A3.Desempenho;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de desempenho da API: mede o tempo de resposta em situação normal
 * (um usuário por vez) e em pico (muitos usuários ao mesmo tempo).
 *
 * <p>Os endpoints medidos são os que qualquer pessoa usa sem login: verificação
 * de saúde, consulta de número, análise de mensagem, blacklist e estatísticas.
 * Os resultados vão para o console e para {@code target/relatorio-desempenho.md},
 * pronto para virar tabela no artigo.</p>
 *
 * <p><b>Como interpretar:</b> o teste roda no próprio computador, com banco H2 em
 * memória, então mede o custo da aplicação (código + Spring + consultas), sem
 * latência de rede nem do banco na nuvem. Os limites abaixo são deliberadamente
 * folgados para o teste não falhar em máquinas lentas; o que importa para o
 * artigo são os números do relatório. Para medir em produção, aponte o mesmo
 * cenário para a URL publicada.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties") // vence backend/config: nunca usar o banco real
class DesempenhoApiTest {

    /** Cenário normal: requisições sequenciais (um usuário). */
    private static final int REQUISICOES_NORMAIS = 150;
    /** Cenário de pico: usuários simultâneos e requisições de cada um. */
    private static final int USUARIOS_NO_PICO = 40;
    private static final int REQUISICOES_POR_USUARIO = 10;

    /** Limites do teste (p95, em milissegundos). */
    private static final long LIMITE_P95_NORMAL_MS = 1_000;
    private static final long LIMITE_P95_PICO_MS = 3_000;

    private static final String MENSAGEM_JSON = "{\"mensagem\":\"Parabéns! Você ganhou um brinde. "
            + "O entregador está a caminho, é só pagar a taxa de entrega na maquininha hoje.\"}";

    @Value("${local.server.port}")
    private int porta;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Uma medida: qual endpoint, quanto demorou e se deu certo. */
    private record Amostra(String endpoint, double ms, boolean ok) {
    }

    /** Resultado de um cenário. */
    private record Medicao(String nome, int usuarios, List<Amostra> amostras, double duracaoSegundos) {

        long erros() {
            return amostras.stream().filter(a -> !a.ok()).count();
        }

        double media() {
            return amostras.stream().mapToDouble(Amostra::ms).average().orElse(0);
        }

        double percentil(double p) {
            return calcularPercentil(amostras, p);
        }

        double maximo() {
            return amostras.stream().mapToDouble(Amostra::ms).max().orElse(0);
        }

        double vazao() {
            return duracaoSegundos == 0 ? 0 : amostras.size() / duracaoSegundos;
        }
    }

    private static double calcularPercentil(List<Amostra> amostras, double p) {
        if (amostras.isEmpty()) {
            return 0;
        }
        List<Double> ordenado = new ArrayList<>();
        amostras.forEach(a -> ordenado.add(a.ms()));
        Collections.sort(ordenado);
        int indice = (int) Math.ceil(p * ordenado.size()) - 1; // posto mais próximo
        return ordenado.get(Math.max(0, Math.min(indice, ordenado.size() - 1)));
    }

    @Test
    void tempoDeRespostaEmCargaNormalEEmPico() throws Exception {
        for (int i = 0; i < 30; i++) {
            disparar(i); // aquecimento: a primeira chamada de cada rota é mais lenta (JIT, caches)
        }

        Medicao normal = rodar("Carga normal", 1, REQUISICOES_NORMAIS);
        Medicao pico = rodar("Pico", USUARIOS_NO_PICO, REQUISICOES_POR_USUARIO);

        String relatorio = montarRelatorio(normal, pico);
        System.out.println(relatorio);
        gravar(relatorio);

        assertEquals(0, normal.erros(), "nenhuma falha em carga normal");
        assertEquals(0, pico.erros(), "nenhuma falha no pico");
        assertTrue(normal.percentil(0.95) < LIMITE_P95_NORMAL_MS,
                "p95 normal = " + normal.percentil(0.95) + " ms");
        assertTrue(pico.percentil(0.95) < LIMITE_P95_PICO_MS,
                "p95 no pico = " + pico.percentil(0.95) + " ms");
    }

    // ------------------------------------------------------------------ execução

    private Medicao rodar(String nome, int usuarios, int requisicoesPorUsuario) throws Exception {
        List<Amostra> amostras = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(usuarios);
        List<Future<?>> tarefas = new ArrayList<>();

        for (int u = 0; u < usuarios; u++) {
            final int offset = u;
            tarefas.add(pool.submit(() -> {
                largada.await(); // todos começam juntos, para simular o pico de verdade
                for (int i = 0; i < requisicoesPorUsuario; i++) {
                    amostras.add(disparar(offset + i));
                }
                return null;
            }));
        }

        long inicio = System.nanoTime();
        largada.countDown();
        for (Future<?> t : tarefas) {
            t.get();
        }
        double duracao = (System.nanoTime() - inicio) / 1_000_000_000.0;
        pool.shutdown();
        return new Medicao(nome, usuarios, amostras, duracao);
    }

    /** Alterna entre os cinco endpoints públicos e mede cada chamada. */
    private Amostra disparar(int indice) {
        String base = "http://localhost:" + porta;
        HttpRequest.Builder b;
        String rotulo;
        switch (indice % 5) {
            case 0 -> {
                rotulo = "GET /saude";
                b = HttpRequest.newBuilder(URI.create(base + "/saude")).GET();
            }
            case 1 -> {
                rotulo = "GET /prevencao/telefone";
                b = HttpRequest.newBuilder(URI.create(base + "/prevencao/telefone?numero=11999990000")).GET();
            }
            case 2 -> {
                rotulo = "POST /prevencao/mensagem";
                b = HttpRequest.newBuilder(URI.create(base + "/prevencao/mensagem"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(MENSAGEM_JSON));
            }
            case 3 -> {
                rotulo = "GET /golpistas/blacklist";
                b = HttpRequest.newBuilder(URI.create(base + "/golpistas/blacklist")).GET();
            }
            default -> {
                rotulo = "GET /denuncias/estatisticas";
                b = HttpRequest.newBuilder(URI.create(base + "/denuncias/estatisticas")).GET();
            }
        }
        long t0 = System.nanoTime();
        boolean ok;
        try {
            HttpResponse<String> r = http.send(b.timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
            ok = r.statusCode() == 200;
        } catch (IOException e) {
            ok = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ok = false;
        }
        return new Amostra(rotulo, (System.nanoTime() - t0) / 1_000_000.0, ok);
    }

    // ------------------------------------------------------------------ relatório

    private String montarRelatorio(Medicao normal, Medicao pico) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Relatório de desempenho da API\n\n");
        sb.append("Tempos em milissegundos. Ambiente: teste local, banco H2 em memória.\n\n");
        sb.append("| Cenário | Usuários | Requisições | Erros | Média | p50 | p95 | p99 | Máx | Vazão (req/s) |\n");
        sb.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (Medicao m : List.of(normal, pico)) {
            sb.append(String.format("| %s | %d | %d | %d | %.1f | %.1f | %.1f | %.1f | %.1f | %.1f |%n",
                    m.nome(), m.usuarios(), m.amostras().size(), m.erros(), m.media(),
                    m.percentil(0.50), m.percentil(0.95), m.percentil(0.99), m.maximo(), m.vazao()));
        }

        sb.append("\n## Por endpoint (cenário de pico)\n\n");
        sb.append("| Endpoint | Requisições | Média | p95 | Máx |\n|---|---|---|---|---|\n");
        Map<String, List<Amostra>> porEndpoint = new LinkedHashMap<>();
        for (Amostra a : pico.amostras()) {
            porEndpoint.computeIfAbsent(a.endpoint(), k -> new ArrayList<>()).add(a);
        }
        porEndpoint.forEach((endpoint, lista) -> sb.append(String.format("| %s | %d | %.1f | %.1f | %.1f |%n",
                endpoint, lista.size(),
                lista.stream().mapToDouble(Amostra::ms).average().orElse(0),
                calcularPercentil(lista, 0.95),
                lista.stream().mapToDouble(Amostra::ms).max().orElse(0))));
        return sb.toString();
    }

    private void gravar(String relatorio) throws IOException {
        Path destino = Path.of("target", "relatorio-desempenho.md");
        Files.createDirectories(destino.getParent());
        Files.writeString(destino, relatorio, StandardCharsets.UTF_8);
    }
}
