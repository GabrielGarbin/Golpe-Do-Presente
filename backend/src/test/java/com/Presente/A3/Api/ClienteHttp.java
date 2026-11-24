package com.Presente.A3.Api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP mínimo para os testes de API.
 *
 * <p>Usa só o {@code java.net.http} do JDK: assim os testes chamam a API de
 * verdade, pela rede (servidor embutido em porta aleatória), sem depender de
 * classes de teste específicas de uma versão do Spring Boot.</p>
 */
final class ClienteHttp {

    /** Resposta HTTP: status, corpo e cabeçalhos. */
    record Resposta(int status, String corpo, HttpHeaders cabecalhos) {

        /** Valor de um campo de um JSON simples (texto, número ou booleano), ou null. */
        String campo(String nome) {
            Matcher m = Pattern.compile("\"" + Pattern.quote(nome) + "\"\\s*:\\s*(?:\"([^\"]*)\"|([-0-9.]+|true|false|null))")
                    .matcher(corpo);
            if (!m.find()) {
                return null;
            }
            return m.group(1) != null ? m.group(1) : m.group(2);
        }

        /** Corpo sem espaços, para comparar trechos de JSON sem se preocupar com formatação. */
        String compacto() {
            return corpo.replaceAll("\\s", "");
        }

        String cabecalho(String nome) {
            return cabecalhos.firstValue(nome).orElse(null);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;

    ClienteHttp(int porta) {
        this.base = "http://localhost:" + porta;
    }

    Resposta get(String caminho, String token) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho)).GET(), token);
    }

    Resposta post(String caminho, String json, String token) {
        return enviar(corpo(caminho, "POST", json), token);
    }

    Resposta put(String caminho, String json, String token) {
        return enviar(corpo(caminho, "PUT", json), token);
    }

    Resposta delete(String caminho, String token) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho)).DELETE(), token);
    }

    /** Pré-verificação de CORS, como o navegador faz antes de enviar o cabeçalho Authorization. */
    Resposta preflight(String caminho, String origem, String metodo, String cabecalhos) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", origem)
                .header("Access-Control-Request-Method", metodo)
                .header("Access-Control-Request-Headers", cabecalhos);
        return enviar(b, null);
    }

    /** GET com um cabeçalho Origin, para conferir os cabeçalhos de CORS na resposta. */
    Resposta getComOrigem(String caminho, String origem) {
        return enviar(HttpRequest.newBuilder(URI.create(base + caminho)).GET().header("Origin", origem), null);
    }

    private HttpRequest.Builder corpo(String caminho, String metodo, String json) {
        return HttpRequest.newBuilder(URI.create(base + caminho))
                .header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(json));
    }

    private Resposta enviar(HttpRequest.Builder b, String token) {
        b.timeout(Duration.ofSeconds(20));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Resposta(r.statusCode(), r.body(), r.headers());
        } catch (IOException e) {
            throw new IllegalStateException("Falha de rede ao chamar a API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida.", e);
        }
    }

    /** Monta um JSON plano a partir de pares chave, valor. Texto vai entre aspas; o resto, cru. */
    static String json(Object... pares) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pares.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pares[i]).append("\":");
            Object v = pares[i + 1];
            if (v instanceof String s) {
                sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.append('}').toString();
    }
}
