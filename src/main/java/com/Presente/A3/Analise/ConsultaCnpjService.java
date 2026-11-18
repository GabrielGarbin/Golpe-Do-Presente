package com.Presente.A3.Analise;

import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Modelo.Confianca;
import com.Presente.A3.Modelo.NivelRisco;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifica a loja que supostamente enviou o brinde, consultando o CNPJ na
 * BrasilAPI (dados abertos da Receita Federal, sem chave de acesso).
 *
 * <p>Sinais considerados: situação cadastral diferente de ATIVA e empresa
 * aberta há pouco tempo. Ambos são <b>indícios</b>, não provas: muita loja
 * legítima é nova, e muito golpista usa o CNPJ de uma empresa real. Por isso o
 * resultado nunca passa de MEDIO sozinho, exceto quando o CNPJ não está ativo.</p>
 *
 * <p>Se a consulta falhar (sem internet, BrasilAPI fora do ar, cota excedida),
 * o resultado volta como inconclusivo, e o sistema segue funcionando.</p>
 */
@Service
public class ConsultaCnpjService {

    private static final System.Logger LOG = System.getLogger(ConsultaCnpjService.class.getName());
    private static final int TAMANHO_CACHE = 300;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String urlBase;
    private final Duration timeout;
    private final LimitadorUso limitador;
    private final Map<String, Dados> cache = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Dados> eldest) {
            return size() > TAMANHO_CACHE;
        }
    };

    /** Dados da empresa que interessam à análise. */
    record Dados(String razaoSocial, String situacao, LocalDate inicioAtividade,
                 String atividade, String municipio, String uf) {
    }

    public ConsultaCnpjService(
            @Value("${app.cnpj.url:https://brasilapi.com.br/api/cnpj/v1}") String urlBase,
            @Value("${app.cnpj.timeout-segundos:8}") int timeoutSegundos,
            @Value("${app.cnpj.limite-por-usuario-dia:30}") int limitePorUsuario,
            @Value("${app.cnpj.limite-global-dia:500}") int limiteGlobal) {
        this.urlBase = urlBase.replaceAll("/+$", "");
        this.timeout = Duration.ofSeconds(timeoutSegundos);
        this.limitador = new LimitadorUso(limitePorUsuario, limiteGlobal);
    }

    /**
     * @param cnpjBruto   CNPJ com ou sem máscara
     * @param solicitante quem pediu (ex.: IP), para limitar o uso
     * @throws IllegalArgumentException se o CNPJ for inválido
     */
    public ResultadoVerificacao verificar(String cnpjBruto, String solicitante) {
        String cnpj = normalizar(cnpjBruto);
        List<String> motivos = new ArrayList<>();

        Dados dados = consultar(cnpj, solicitante == null ? "anonimo" : solicitante);
        if (dados == null) {
            motivos.add("Não foi possível consultar este CNPJ agora (serviço indisponível ou limite de consultas).");
            motivos.add("Sem dados suficientes: isso NÃO significa que a empresa seja confiável.");
            return new ResultadoVerificacao(NivelRisco.BAIXO, 0, motivos, recomendacoes(NivelRisco.BAIXO),
                    Confianca.BAIXA, true, List.of());
        }

        int pontos = 0;
        motivos.add("Empresa: " + dados.razaoSocial()
                + (dados.municipio().isBlank() ? "" : " (" + dados.municipio() + "/" + dados.uf() + ")")
                + (dados.atividade().isBlank() ? "" : " - atividade: " + dados.atividade()));

        boolean ativa = "ATIVA".equalsIgnoreCase(dados.situacao());
        if (!ativa) {
            pontos += 60;
            motivos.add("Situação cadastral na Receita: " + dados.situacao() + " (+60). Empresa sem registro ativo não deveria estar vendendo.");
        } else {
            motivos.add("Situação cadastral na Receita: ATIVA.");
        }
        if (dados.inicioAtividade() != null) {
            long dias = ChronoUnit.DAYS.between(dados.inicioAtividade(), LocalDate.now());
            if (dias < 180) {
                pontos += 30;
                motivos.add("Empresa aberta há apenas " + Math.max(dias, 0) + " dia(s) (+30).");
            } else if (dias < 365) {
                pontos += 15;
                motivos.add("Empresa aberta há menos de 1 ano (+15).");
            } else {
                motivos.add("Empresa aberta desde " + dados.inicioAtividade() + ".");
            }
        }
        motivos.add("Atenção: golpistas também usam CNPJ de empresas reais. Confira se o telefone/site de contato é o oficial da empresa.");

        NivelRisco nivel = pontos >= 60 ? NivelRisco.ALTO : pontos >= 30 ? NivelRisco.MEDIO : NivelRisco.BAIXO;
        return new ResultadoVerificacao(nivel, Math.min(100, pontos), motivos, recomendacoes(nivel),
                Confianca.MEDIA, false, List.of("CNPJ_RECEITA"));
    }

    private Dados consultar(String cnpj, String solicitante) {
        synchronized (cache) {
            Dados emCache = cache.get(cnpj);
            if (emCache != null) {
                return emCache;
            }
        }
        if (!limitador.tentar(solicitante)) {
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(urlBase + "/" + cnpj))
                    .timeout(timeout).header("accept", "application/json").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                LOG.log(System.Logger.Level.WARNING, "Consulta de CNPJ respondeu HTTP " + resp.statusCode());
                return null;
            }
            Dados dados = interpretar(resp.body());
            if (dados != null) {
                synchronized (cache) {
                    cache.put(cnpj, dados);
                }
            }
            return dados;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Falha ao consultar CNPJ: " + e.getClass().getSimpleName());
            return null;
        }
    }

    static Dados interpretar(String json) {
        String situacao = JsonMinimo.extrairString(json, "descricao_situacao_cadastral");
        if (situacao == null) {
            return null; // resposta fora do formato esperado
        }
        String razao = vazioSe(JsonMinimo.extrairString(json, "razao_social"));
        LocalDate inicio = null;
        String data = JsonMinimo.extrairString(json, "data_inicio_atividade");
        if (data != null) {
            try {
                inicio = LocalDate.parse(data);
            } catch (Exception ignorado) {
                // data ausente ou em formato inesperado: a análise segue sem esse sinal
            }
        }
        return new Dados(razao.isBlank() ? "(razão social não informada)" : razao, situacao.trim(), inicio,
                vazioSe(JsonMinimo.extrairString(json, "cnae_fiscal_descricao")),
                vazioSe(JsonMinimo.extrairString(json, "municipio")),
                vazioSe(JsonMinimo.extrairString(json, "uf")));
    }

    private static String vazioSe(String s) {
        return s == null ? "" : s.replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    /** Remove a máscara e confere os dois dígitos verificadores. */
    static String normalizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            throw new IllegalArgumentException("CNPJ é obrigatório.");
        }
        String d = bruto.replaceAll("\\D", "");
        if (d.length() != 14 || d.chars().distinct().count() == 1 || !digitosConferem(d)) {
            throw new IllegalArgumentException("CNPJ inválido: informe os 14 dígitos corretos.");
        }
        return d;
    }

    private static boolean digitosConferem(String d) {
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return digito(d, p1) == d.charAt(12) - '0' && digito(d, p2) == d.charAt(13) - '0';
    }

    private static int digito(String d, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += (d.charAt(i) - '0') * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static List<String> recomendacoes(NivelRisco nivel) {
        List<String> r = new ArrayList<>();
        if (nivel == NivelRisco.ALTO) {
            r.add("Não pague nada: o CNPJ informado não está ativo na Receita.");
        } else if (nivel == NivelRisco.MEDIO) {
            r.add("Empresa recente: confirme o contato oficial antes de qualquer pagamento.");
        } else {
            r.add("O CNPJ por si só não prova que a mensagem seja legítima. Confirme o contato oficial da loja.");
        }
        r.add("Nenhuma loja legítima cobra para entregar um brinde.");
        return r;
    }
}
