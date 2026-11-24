package com.Presente.A3.Api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes dos endpoints da API REST: respostas para pedidos válidos e
 * tratamento de erro para pedidos inválidos, além do fluxo colaborativo
 * (várias pessoas denunciando o mesmo número) e do CRUD administrativo.
 */
class EndpointsApiTest extends ApiTestBase {

    // ------------------------------------------------------------------ públicos

    @Test
    void saudeEstaDisponivelSemLogin() {
        ClienteHttp.Resposta r = http().get("/saude", null);
        assertEquals(200, r.status());
        assertEquals("UP", r.campo("status"));
    }

    @Test
    void prevencaoFuncionaSemLogin() {
        ClienteHttp.Resposta tel = http().get("/prevencao/telefone?numero=" + telefoneAleatorio(), null);
        assertEquals(200, tel.status());
        assertEquals("BAIXO", tel.campo("nivelRisco"), "número sem denúncias");

        assertEquals(200, http().get("/prevencao/checklist", null).status());

        ClienteHttp.Resposta msg = http().post("/prevencao/mensagem",
                ClienteHttp.json("mensagem", "Parabéns! Você ganhou um brinde. O entregador está a caminho, "
                        + "só pagar a taxa de entrega na maquininha hoje."), null);
        assertEquals(200, msg.status());
        assertNotNull(msg.campo("nivelRisco"));
        assertNotNull(msg.campo("pontuacao"));
    }

    @Test
    void mensagemTipicaDoGolpePontuaMaisQueUmaMensagemComum() {
        int golpe = Integer.parseInt(http().post("/prevencao/mensagem", ClienteHttp.json("mensagem",
                "Você ganhou um brinde! O entregador está a caminho, pague a taxa de entrega na maquininha hoje."), null)
                .campo("pontuacao"));
        int comum = Integer.parseInt(http().post("/prevencao/mensagem",
                ClienteHttp.json("mensagem", "Oi, tudo bem? Vamos almoçar amanhã?"), null).campo("pontuacao"));

        assertTrue(golpe > comum, "golpe=" + golpe + " comum=" + comum);
    }

    @Test
    void estatisticasEBlacklistSaoPublicas() {
        ClienteHttp.Resposta est = http().get("/denuncias/estatisticas", null);
        assertEquals(200, est.status());
        assertNotNull(est.campo("totalDenuncias"));
        assertNotNull(est.campo("limiteConfirmacao"));

        ClienteHttp.Resposta bl = http().get("/golpistas/blacklist", null);
        assertEquals(200, bl.status());
        assertTrue(bl.corpo().trim().startsWith("["));
    }

    @Test
    void entradasInvalidasRetornam400ComMensagemEmJson() {
        ClienteHttp.Resposta r = http().get("/prevencao/telefone?numero=123", null);
        assertEquals(400, r.status());
        assertNotNull(r.campo("erro"));

        assertEquals(400, http().post("/prevencao/mensagem", ClienteHttp.json("mensagem", "   "), null).status());
    }

    // ------------------------------------------------------------------ denúncia colaborativa

    @Test
    void tresPessoasDiferentesConfirmamONumeroEEleEntraNaBlacklist() {
        String golpista = telefoneAleatorio();
        String[] tokens = {novoUsuarioToken(), novoUsuarioToken(), novoUsuarioToken()};

        for (int i = 0; i < 3; i++) {
            ClienteHttp.Resposta d = http().post("/denuncias", ClienteHttp.json("telefoneGolpista", golpista,
                    "canal", "WHATSAPP", "estabelecimentoFalsificado", "Floricultura Teste",
                    "descricao", "Disse que eu ganhei um brinde e cobrou taxa na maquininha."), tokens[i]);
            assertEquals(201, d.status(), d.corpo());

            String nivel = http().get("/prevencao/telefone?numero=" + golpista, null).campo("nivelRisco");
            assertEquals(i < 2 ? "MEDIO" : "ALTO", nivel, "depois da denúncia " + (i + 1));
        }

        assertTrue(http().get("/golpistas/blacklist", null).corpo().contains(golpista),
                "o número confirmado aparece na blacklist pública");
    }

    @Test
    void denuncianteVemDoTokenENaoDoCorpoDaRequisicao() {
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        String idReal = reg.campo("id");

        // Tenta se passar pelo cliente 999999: o servidor deve ignorar esse valor.
        ClienteHttp.Resposta d = http().post("/denuncias", ClienteHttp.json("clienteId", 999999,
                "telefoneGolpista", telefoneAleatorio(), "canal", "SMS"), reg.campo("token"));
        assertEquals(201, d.status(), d.corpo());
        assertEquals(idReal, d.campo("clienteId"));
        assertFalse(d.compacto().contains("\"clienteId\":999999"));
    }

    @Test
    void denunciaInvalidaOuRepetidaTemErroEspecifico() {
        String token = novoUsuarioToken();
        String golpista = telefoneAleatorio();
        String corpo = ClienteHttp.json("telefoneGolpista", golpista, "canal", "SMS");

        assertEquals(201, http().post("/denuncias", corpo, token).status());
        assertEquals(409, http().post("/denuncias", corpo, token).status(), "mesma pessoa, mesmo número");
        assertEquals(400, http().post("/denuncias", ClienteHttp.json("telefoneGolpista", "12"), token).status(), "telefone inválido");
        assertEquals(400, http().post("/denuncias", ClienteHttp.json("telefoneGolpista", golpista, "canal", "FAX"), token).status(), "canal inexistente");
    }

    @Test
    void naoEPossivelDenunciarOProprioNumero() {
        String telefone = telefoneAleatorio();
        String token = registrar(emailAleatorio(), telefone).campo("token");

        ClienteHttp.Resposta r = http().post("/denuncias", ClienteHttp.json("telefoneGolpista", telefone), token);

        assertEquals(400, r.status());
    }

    // ------------------------------------------------------------------ CRUD administrativo

    @Test
    void adminFazCrudCompletoDeGolpista() {
        String admin = tokenAdmin();
        String telefone = telefoneAleatorio();

        ClienteHttp.Resposta criado = http().post("/golpistas", ClienteHttp.json("nome", "Golpista Teste", "telefone", telefone), admin);
        assertTrue(criado.status() == 200 || criado.status() == 201, "criar: " + criado.status());
        String id = criado.campo("id");
        assertNotNull(id);

        assertEquals(200, http().get("/golpistas/" + id, admin).status());

        ClienteHttp.Resposta atualizado = http().put("/golpistas/" + id, ClienteHttp.json("nome", "Nome Novo", "telefone", telefone), admin);
        assertEquals(200, atualizado.status());
        assertEquals("Nome Novo", atualizado.campo("nome"));

        assertEquals(409, http().post("/golpistas", ClienteHttp.json("nome", "Duplicado", "telefone", telefone), admin).status(), "telefone repetido");

        assertEquals(200, http().delete("/golpistas/" + id, admin).status());
        assertEquals(404, http().get("/golpistas/" + id, admin).status());
        assertEquals(404, http().delete("/golpistas/" + id, admin).status());
    }

    @Test
    void adminFazCrudCompletoDeCliente() {
        String admin = tokenAdmin();

        ClienteHttp.Resposta criado = http().post("/clientes",
                ClienteHttp.json("nome", "Cliente CRUD", "email", emailAleatorio(), "telefone", telefoneAleatorio()), admin);
        assertTrue(criado.status() == 200 || criado.status() == 201, "criar: " + criado.status());
        String id = criado.campo("id");

        assertEquals(200, http().get("/clientes/" + id, admin).status());
        ClienteHttp.Resposta atualizado = http().put("/clientes/" + id,
                ClienteHttp.json("nome", "Cliente Alterado", "email", emailAleatorio(), "telefone", telefoneAleatorio()), admin);
        assertEquals("Cliente Alterado", atualizado.campo("nome"));

        assertEquals(200, http().delete("/clientes/" + id, admin).status());
        assertEquals(404, http().get("/clientes/" + id, admin).status());
    }

    @Test
    void usuarioLogadoVeAListaCompletaDeGolpistasMasNaoAltera() {
        String token = novoUsuarioToken();
        assertEquals(200, http().get("/golpistas", token).status());
        assertEquals(403, http().delete("/golpistas/1", token).status());
    }
}
