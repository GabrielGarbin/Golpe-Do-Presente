package com.Presente.A3.Api;

import com.Presente.A3.Modelo.CanalContato;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import com.Presente.A3.Repositorio.DenunciaRepository;
import com.Presente.A3.Repositorio.GolpistaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integração front-end ↔ back-end ↔ banco de dados.
 *
 * <p>Cada teste faz pela rede exatamente as chamadas que o front faz
 * ({@code frontend/js/api.js} e {@code frontend/js/views/denunciar.js}), com o
 * mesmo formato de JSON, e depois confere <b>direto no banco</b> (pelos
 * repositórios) se a denúncia foi gravada com todos os campos.</p>
 *
 * <p>Roda com o perfil "test" (H2 em memória, modo PostgreSQL), igual aos
 * outros testes de API: não toca no Supabase.</p>
 */
class IntegracaoFrontBackApiTest extends ApiTestBase {

    /** Origem do front em desenvolvimento (app.cors.origens no perfil de teste). */
    private static final String ORIGEM_FRONT = "http://localhost:5500";

    @Autowired
    private DenunciaRepository denunciaRepo;
    @Autowired
    private GolpistaRepository golpistaRepo;

    /** Telefone como o campo com máscara do front envia: "(11) 98765-4321". */
    private static String formatadoComoNoFront(String digitos) {
        return "(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7);
    }

    // ------------------------------------------------------------------ fluxo completo

    @Test
    void denunciaEnviadaPeloFrontEhGravadaNoBancoComTodosOsCampos() {
        // 1) Cadastro, como o diálogo "Criar conta" do front.
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        assertEquals(201, reg.status(), reg.corpo());
        String token = reg.campo("token");
        Long clienteId = Long.valueOf(reg.campo("id"));

        // 2) Denúncia com o mesmo corpo que denunciar.js monta (telefone com máscara,
        //    valores anunciado/cobrado anexados ao relato).
        String golpista = telefoneAleatorio();
        String relato = "Entregador trouxe flores e cobrou na maquininha. "
                + "[Valor anunciado: R$ 9,90 | cobrado: R$ 990,00]";
        long antes = denunciaRepo.count();

        ClienteHttp.Resposta r = http().post("/denuncias", ClienteHttp.json(
                "telefoneGolpista", formatadoComoNoFront(golpista),
                "canal", "ENTREGADOR_PRESENCIAL",
                "estabelecimentoFalsificado", "Floricultura Bela Flor",
                "descricao", relato), token);

        assertEquals(201, r.status(), r.corpo());
        Long id = Long.valueOf(r.campo("id"));

        // 3) Conferência direto no banco.
        assertEquals(antes + 1, denunciaRepo.count(), "exatamente uma linha nova em denuncias");
        Denuncia gravada = denunciaRepo.findById(id).orElseThrow();
        assertEquals(clienteId, gravada.getClienteId(), "denunciante = dono do token");
        assertEquals(CanalContato.ENTREGADOR_PRESENCIAL, gravada.getCanal());
        assertEquals("Floricultura Bela Flor", gravada.getEstabelecimentoFalsificado());
        assertEquals(relato, gravada.getDescricao(), "relato gravado sem cortes, com os valores");
        assertNotNull(gravada.getData(), "data preenchida pelo servidor");

        // 4) O golpista foi criado/atualizado com o telefone só em dígitos.
        Golpista g = golpistaRepo.findByTelefone(golpista).orElseThrow();
        assertEquals(g.getId(), gravada.getGolpistaId());
        assertEquals(1, g.getTotalDenuncias());
        assertEquals(StatusGolpista.SUSPEITO, g.getStatus());
        assertNotNull(g.getDataPrimeiraDenuncia());
    }

    @Test
    void camposOpcionaisVaziosDoFormularioSaoGravadosComoNulo() {
        // O front envia null quando "Loja imitada" e "O que aconteceu" ficam em branco.
        String token = novoUsuarioToken();
        ClienteHttp.Resposta r = http().post("/denuncias", ClienteHttp.json(
                "telefoneGolpista", formatadoComoNoFront(telefoneAleatorio()),
                "canal", "WHATSAPP",
                "estabelecimentoFalsificado", null,
                "descricao", null), token);

        assertEquals(201, r.status(), r.corpo());
        Denuncia gravada = denunciaRepo.findById(Long.valueOf(r.campo("id"))).orElseThrow();
        assertEquals(CanalContato.WHATSAPP, gravada.getCanal());
        assertNull(gravada.getEstabelecimentoFalsificado());
        assertNull(gravada.getDescricao());
    }

    @Test
    void denunciaGravadaAlimentaAConsultaPublicaEAsEstatisticas() {
        String golpista = telefoneAleatorio();
        int totalAntes = Integer.parseInt(http().get("/denuncias/estatisticas", null).campo("totalDenuncias"));

        ClienteHttp.Resposta r = http().post("/denuncias",
                ClienteHttp.json("telefoneGolpista", golpista, "canal", "SMS"), novoUsuarioToken());
        assertEquals(201, r.status(), r.corpo());

        // A aba "Consultar número" lê o que acabou de ser gravado.
        assertEquals("MEDIO", http().get("/prevencao/telefone?numero=" + golpista, null).campo("nivelRisco"));
        // O painel (indicadores) também.
        int totalDepois = Integer.parseInt(http().get("/denuncias/estatisticas", null).campo("totalDenuncias"));
        assertEquals(totalAntes + 1, totalDepois);
    }

    @Test
    void adminLeADenunciaGravadaMasClienteComumNao() {
        String relato = "Relato para conferir a leitura do admin " + System.nanoTime();
        ClienteHttp.Resposta r = http().post("/denuncias", ClienteHttp.json(
                "telefoneGolpista", telefoneAleatorio(), "canal", "LIGACAO", "descricao", relato), novoUsuarioToken());
        assertEquals(201, r.status(), r.corpo());
        String id = r.campo("id");

        ClienteHttp.Resposta comoAdmin = http().get("/denuncias/" + id, tokenAdmin());
        assertEquals(200, comoAdmin.status());
        assertEquals(relato, comoAdmin.campo("descricao"));
        assertTrue(http().get("/denuncias", tokenAdmin()).corpo().contains(relato), "aparece na listagem do admin");

        assertEquals(403, http().get("/denuncias/" + id, novoUsuarioToken()).status(), "cliente comum não lê denúncias");
    }

    // ------------------------------------------------------------------ falhas não gravam nada

    @Test
    void semLoginADenunciaERecusadaENadaEGravado() {
        long antes = denunciaRepo.count();

        ClienteHttp.Resposta r = http().post("/denuncias",
                ClienteHttp.json("telefoneGolpista", telefoneAleatorio(), "canal", "SMS"), null);

        assertEquals(401, r.status());
        assertNotNull(r.campo("erro"), "o front mostra o campo 'erro' ao usuário");
        assertEquals(antes, denunciaRepo.count());
    }

    @Test
    void denunciaInvalidaNaoDeixaRastroNoBanco() {
        String token = novoUsuarioToken();
        long antes = denunciaRepo.count();
        long golpistasAntes = golpistaRepo.count();

        assertEquals(400, http().post("/denuncias",
                ClienteHttp.json("telefoneGolpista", "(11) 123", "canal", "SMS"), token).status());
        assertEquals(400, http().post("/denuncias",
                ClienteHttp.json("telefoneGolpista", telefoneAleatorio(), "canal", "FAX"), token).status());

        assertEquals(antes, denunciaRepo.count());
        assertEquals(golpistasAntes, golpistaRepo.count(), "nenhum golpista 'fantasma' criado");
    }

    // ------------------------------------------------------------------ contrato com o front

    @Test
    void loginDevolveOPerfilQueOFrontUsaParaMostrarNumerosCompletosAoAdmin() {
        // session.js decide entre número completo e mascarado pelo campo "perfil".
        String admin = tokenAdmin();
        assertEquals("ADMIN", http().get("/auth/eu", admin).campo("perfil"));
        assertEquals("USUARIO", http().get("/auth/eu", novoUsuarioToken()).campo("perfil"));
    }

    @Test
    void navegadorNaOrigemDoFrontPodeEnviarDenunciaComToken() {
        // Antes do POST com Authorization, o navegador faz a pré-verificação de CORS.
        ClienteHttp.Resposta pre = http().preflight("/denuncias", ORIGEM_FRONT, "POST", "authorization,content-type");
        assertTrue(pre.status() == 200 || pre.status() == 204, "preflight: " + pre.status());
        assertEquals(ORIGEM_FRONT, pre.cabecalho("Access-Control-Allow-Origin"));
        String permitidos = String.valueOf(pre.cabecalho("Access-Control-Allow-Headers")).toLowerCase();
        assertTrue(permitidos.contains("authorization") && permitidos.contains("content-type"), permitidos);
    }
}
