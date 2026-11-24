package com.Presente.A3.Api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de ponta a ponta da autenticação JWT: cadastro, login, proteção dos
 * endpoints, perfis (usuário x admin), força bruta e CORS.
 */
class AutenticacaoApiTest extends ApiTestBase {

    @Test
    void registroCriaContaEDevolveTokenSemExporSenha() {
        ClienteHttp.Resposta r = registrar(emailAleatorio(), telefoneAleatorio());

        assertEquals(201, r.status());
        assertEquals(3, r.campo("token").split("\\.").length, "o token JWT tem 3 partes");
        assertEquals("Bearer", r.campo("tipo"));
        assertEquals("USUARIO", r.campo("perfil"));
        assertFalse(r.corpo().toLowerCase().contains("senha"), "a resposta não pode conter senha nem hash");
    }

    @Test
    void registroComEmailRepetidoRetorna409() {
        String email = emailAleatorio();
        assertEquals(201, registrar(email, telefoneAleatorio()).status());

        ClienteHttp.Resposta repetido = registrar(email.toUpperCase(), telefoneAleatorio());

        assertEquals(409, repetido.status());
        assertNotNull(repetido.campo("erro"));
    }

    @Test
    void registroComDadosInvalidosRetorna400() {
        ClienteHttp h = http();
        String ok = ClienteHttp.json("nome", "Fulano", "email", emailAleatorio(), "telefone", telefoneAleatorio(), "senha", SENHA);
        assertEquals(201, h.post("/auth/registro", ok, null).status());

        assertEquals(400, h.post("/auth/registro", ClienteHttp.json("nome", "Fulano", "email", emailAleatorio(),
                "telefone", telefoneAleatorio(), "senha", "curta"), null).status(), "senha curta");
        assertEquals(400, h.post("/auth/registro", ClienteHttp.json("nome", "Fulano", "email", "sem-arroba",
                "telefone", telefoneAleatorio(), "senha", SENHA), null).status(), "e-mail inválido");
        assertEquals(400, h.post("/auth/registro", ClienteHttp.json("nome", "Fulano", "email", emailAleatorio(),
                "telefone", "123", "senha", SENHA), null).status(), "telefone inválido");
        assertEquals(400, h.post("/auth/registro", "{isto não é json", null).status(), "JSON malformado");
    }

    @Test
    void loginCorretoDevolveTokenEErroNaoRevelaSeOEmailExiste() {
        String email = emailAleatorio();
        registrar(email, telefoneAleatorio());

        ClienteHttp.Resposta ok = http().post("/auth/login", ClienteHttp.json("email", email, "senha", SENHA), null);
        assertEquals(200, ok.status());
        assertNotNull(ok.campo("token"));

        ClienteHttp.Resposta senhaErrada = http().post("/auth/login", ClienteHttp.json("email", email, "senha", "errada-errada"), null);
        ClienteHttp.Resposta semConta = http().post("/auth/login",
                ClienteHttp.json("email", emailAleatorio(), "senha", "errada-errada"), null);

        assertEquals(401, senhaErrada.status());
        assertEquals(401, semConta.status());
        assertEquals(senhaErrada.campo("erro"), semConta.campo("erro"), "mesma mensagem: não permite descobrir e-mails cadastrados");
    }

    @Test
    void endpointProtegidoSemTokenRetorna401() {
        ClienteHttp.Resposta r = http().post("/denuncias",
                ClienteHttp.json("telefoneGolpista", telefoneAleatorio()), null);

        assertEquals(401, r.status());
        assertEquals("Bearer", r.cabecalho("WWW-Authenticate"));
        assertNotNull(r.campo("erro"));
        assertEquals(401, http().get("/golpistas", null).status());
    }

    @Test
    void tokenAdulteradoOuLixoRetorna401() {
        String token = novoUsuarioToken();
        char ultimo = token.charAt(token.length() - 1);
        String adulterado = token.substring(0, token.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertEquals(401, http().get("/auth/eu", adulterado).status(), "assinatura alterada");
        assertEquals(401, http().get("/auth/eu", "isto.nao.eh-um-token").status(), "lixo");
        assertEquals(200, http().get("/auth/eu", token).status(), "o token original continua válido");
    }

    @Test
    void authEuDevolveOsDadosDeQuemEstaLogado() {
        String email = emailAleatorio();
        String token = registrar(email, telefoneAleatorio()).campo("token");

        ClienteHttp.Resposta eu = http().get("/auth/eu", token);

        assertEquals(200, eu.status());
        assertEquals(email, eu.campo("email"));
    }

    @Test
    void usuarioComumNaoAcessaAreaDeAdminMasAdminAcessa() {
        String usuario = novoUsuarioToken();
        String admin = tokenAdmin();

        assertEquals(403, http().get("/clientes", usuario).status());
        assertEquals(403, http().get("/denuncias", usuario).status());
        assertEquals(403, http().post("/golpistas",
                ClienteHttp.json("nome", "X", "telefone", telefoneAleatorio()), usuario).status());

        assertEquals(200, http().get("/clientes", admin).status());
        assertEquals(200, http().get("/denuncias", admin).status());
        assertEquals("ADMIN", http().get("/auth/eu", admin).campo("perfil"));
    }

    @Test
    void forcaBrutaBloqueiaComRespostaDe429() {
        String email = emailAleatorio();
        registrar(email, telefoneAleatorio());

        for (int i = 0; i < 5; i++) {
            assertEquals(401, http().post("/auth/login", ClienteHttp.json("email", email, "senha", "errada-" + i + "-xx"), null).status());
        }
        ClienteHttp.Resposta bloqueado = http().post("/auth/login", ClienteHttp.json("email", email, "senha", SENHA), null);

        assertEquals(429, bloqueado.status(), "depois de 5 falhas, até a senha certa fica bloqueada por um tempo");
    }

    @Test
    void corsLiberaOCabecalhoAuthorizationEAsRespostasDeErroTambemTemCors() {
        String origem = "http://localhost:5500";

        ClienteHttp.Resposta pre = http().preflight("/denuncias", origem, "POST", "authorization,content-type");
        assertTrue(pre.status() == 200 || pre.status() == 204, "pré-verificação aceita: " + pre.status());
        String permitidos = pre.cabecalho("Access-Control-Allow-Headers");
        assertNotNull(permitidos);
        assertTrue(permitidos.toLowerCase().contains("authorization"));

        // O 401 precisa carregar o CORS, senão o navegador esconde o erro e mostra "falha de rede".
        ClienteHttp.Resposta negado = http().getComOrigem("/golpistas", origem);
        assertEquals(401, negado.status());
        assertEquals(origem, negado.cabecalho("Access-Control-Allow-Origin"));

        ClienteHttp.Resposta outraOrigem = http().getComOrigem("/golpistas", "http://site-malicioso.example");
        assertNotEquals(origem, outraOrigem.cabecalho("Access-Control-Allow-Origin"));
    }
}
