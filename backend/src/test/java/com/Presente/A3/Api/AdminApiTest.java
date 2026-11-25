package com.Presente.A3.Api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Área de administração ({@code /admin}) de ponta a ponta: controle de acesso,
 * gestão de usuários, promoção de administradores e moderação de denúncias.
 */
class AdminApiTest extends ApiTestBase {

    private static final String NOVA_SENHA = "outra-senha-forte-456";

    // ------------------------------------------------------------ acesso

    @Test
    void rotasDeAdminExigemAdministrador() {
        String usuario = novoUsuarioToken();
        for (String rota : new String[]{"/admin/resumo", "/admin/usuarios", "/admin/denuncias"}) {
            assertEquals(401, http().get(rota, null).status(), rota + " sem login");
            assertEquals(403, http().get(rota, usuario).status(), rota + " com usuário comum");
            assertEquals(200, http().get(rota, tokenAdmin()).status(), rota + " com admin");
        }
        assertEquals(403, http().delete("/admin/denuncias/1", usuario).status());
        assertEquals(403, http().post("/admin/usuarios", ClienteHttp.json("nome", "X"), usuario).status());

        // Só o admin fixo recebe adminFixo=true (libera as mensagens de exemplo no front)
        assertEquals("true", http().get("/auth/eu", tokenAdmin()).campo("adminFixo"));
        assertEquals("false", http().get("/auth/eu", usuario).campo("adminFixo"));
    }

    // ---------------------------------------------------------- usuários

    @Test
    void adminCadastraEditaRedefineSenhaEExcluiUsuario() {
        String admin = tokenAdmin();
        String email = emailAleatorio();

        ClienteHttp.Resposta criado = http().post("/admin/usuarios", ClienteHttp.json("nome", "Criado Pelo Admin",
                "email", email, "telefone", telefoneAleatorio(), "senha", SENHA), admin);
        assertEquals(201, criado.status(), criado.corpo());
        assertEquals("USUARIO", criado.campo("perfil"));
        assertFalse(criado.corpo().toLowerCase().contains("hash"), "a resposta não pode expor o hash");
        String id = criado.campo("id");

        assertEquals(200, login(email, SENHA).status(), "a conta criada pelo admin já consegue entrar");

        ClienteHttp.Resposta editado = http().put("/admin/usuarios/" + id, ClienteHttp.json("nome", "Nome Editado",
                "email", email, "telefone", telefoneAleatorio()), admin);
        assertEquals(200, editado.status(), editado.corpo());
        assertEquals("Nome Editado", editado.campo("nome"));

        assertEquals(204, http().put("/admin/usuarios/" + id + "/senha",
                ClienteHttp.json("senha", NOVA_SENHA), admin).status());
        assertEquals(401, login(email, SENHA).status(), "a senha antiga deixa de valer");
        assertEquals(200, login(email, NOVA_SENHA).status(), "a nova senha funciona");

        assertEquals(204, http().delete("/admin/usuarios/" + id, admin).status());
        assertEquals(404, http().get("/admin/usuarios/" + id, admin).status());
        assertEquals(401, login(email, NOVA_SENHA).status(), "conta excluída não entra mais");
    }

    @Test
    void cadastroPeloAdminValidaDadosEDuplicidade() {
        String admin = tokenAdmin();
        String email = emailAleatorio();
        String telefone = telefoneAleatorio();
        assertEquals(201, http().post("/admin/usuarios", ClienteHttp.json("nome", "Primeiro",
                "email", email, "telefone", telefone, "senha", SENHA), admin).status());

        assertEquals(409, http().post("/admin/usuarios", ClienteHttp.json("nome", "Outro",
                "email", email.toUpperCase(), "telefone", telefoneAleatorio(), "senha", SENHA), admin).status(),
                "e-mail repetido");
        assertEquals(409, http().post("/admin/usuarios", ClienteHttp.json("nome", "Outro",
                "email", emailAleatorio(), "telefone", telefone, "senha", SENHA), admin).status(),
                "telefone repetido");
        assertEquals(400, http().post("/admin/usuarios", ClienteHttp.json("nome", "Outro",
                "email", emailAleatorio(), "telefone", telefoneAleatorio(), "senha", "curta"), admin).status(),
                "senha curta");
        assertEquals(400, http().post("/admin/usuarios", ClienteHttp.json("nome", "Outro",
                "email", emailAleatorio(), "telefone", telefoneAleatorio(), "senha", SENHA, "perfil", "CHEFE"),
                admin).status(), "perfil inválido");
    }

    @Test
    void promoverDaAcessoNaHoraERebaixarRetira() {
        String admin = tokenAdmin();
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        String token = reg.campo("token");
        String id = reg.campo("id");

        assertEquals(403, http().get("/admin/resumo", token).status());

        ClienteHttp.Resposta promovido = http().put("/admin/usuarios/" + id + "/perfil",
                ClienteHttp.json("perfil", "ADMIN"), admin);
        assertEquals(200, promovido.status(), promovido.corpo());
        assertEquals("ADMIN", promovido.campo("perfil"));
        // Mesmo token de antes: o acesso de admin é conferido no banco, sem precisar sair e entrar.
        assertEquals(200, http().get("/admin/resumo", token).status(), "promoção vale na hora");
        assertEquals("ADMIN", http().get("/auth/eu", token).campo("perfil"));

        assertEquals(200, http().put("/admin/usuarios/" + id + "/perfil",
                ClienteHttp.json("perfil", "USUARIO"), admin).status());
        assertEquals(403, http().get("/admin/resumo", token).status(), "rebaixamento vale na hora");
        assertEquals("USUARIO", http().get("/auth/eu", token).campo("perfil"));
    }

    @Test
    void adminPromovidoNaoPodeSeRebaixarNemSeExcluir() {
        String admin = tokenAdmin();
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        String token = reg.campo("token");
        String id = reg.campo("id");
        assertEquals(200, http().put("/admin/usuarios/" + id + "/perfil",
                ClienteHttp.json("perfil", "ADMIN"), admin).status());

        assertEquals(409, http().put("/admin/usuarios/" + id + "/perfil",
                ClienteHttp.json("perfil", "USUARIO"), token).status(), "não pode se rebaixar");
        assertEquals(409, http().delete("/admin/usuarios/" + id, token).status(), "não pode se excluir");
    }

    @Test
    void adminFixoNaoPodeSerRebaixadoNemExcluido() {
        String admin = tokenAdmin();
        String idAdmin = http().get("/auth/eu", admin).campo("id");

        ClienteHttp.Resposta lista = http().get("/admin/usuarios", admin);
        assertTrue(lista.compacto().contains("\"adminFixo\":true"), lista.corpo());

        assertEquals(409, http().put("/admin/usuarios/" + idAdmin + "/perfil",
                ClienteHttp.json("perfil", "USUARIO"), admin).status());
        assertEquals(409, http().delete("/admin/usuarios/" + idAdmin, admin).status());
    }

    @Test
    void soOAdminFixoAlteraOutrosAdministradores() {
        String fixo = tokenAdmin();
        String idFixo = http().get("/auth/eu", fixo).campo("id");

        ClienteHttp.Resposta regA = registrar(emailAleatorio(), telefoneAleatorio());
        ClienteHttp.Resposta regB = registrar(emailAleatorio(), telefoneAleatorio());
        String tokenA = regA.campo("token");
        String idA = regA.campo("id");
        String idB = regB.campo("id");
        assertEquals(200, http().put("/admin/usuarios/" + idA + "/perfil", ClienteHttp.json("perfil", "ADMIN"), fixo).status());
        assertEquals(200, http().put("/admin/usuarios/" + idB + "/perfil", ClienteHttp.json("perfil", "ADMIN"), fixo).status());

        // Admin promovido NÃO mexe no admin fixo
        assertEquals(409, http().put("/admin/usuarios/" + idFixo, ClienteHttp.json("nome", "Invasor",
                "email", EMAIL_ADMIN, "telefone", telefoneAleatorio()), tokenA).status(), "editar fixo");
        assertEquals(409, http().put("/admin/usuarios/" + idFixo + "/senha",
                ClienteHttp.json("senha", NOVA_SENHA), tokenA).status(), "senha do fixo");
        assertEquals(200, login(EMAIL_ADMIN, SENHA).status(), "a senha do fixo continua a mesma");

        // Admin promovido NÃO mexe em outro promovido
        assertEquals(409, http().put("/admin/usuarios/" + idB + "/senha",
                ClienteHttp.json("senha", NOVA_SENHA), tokenA).status(), "senha de outro admin");
        assertEquals(409, http().delete("/admin/usuarios/" + idB, tokenA).status(), "excluir outro admin");
        assertEquals(409, http().put("/admin/usuarios/" + idB + "/perfil",
                ClienteHttp.json("perfil", "USUARIO"), tokenA).status(), "rebaixar outro admin");

        // O promovido altera os próprios dados
        assertEquals(204, http().put("/admin/usuarios/" + idA + "/senha",
                ClienteHttp.json("senha", NOVA_SENHA), tokenA).status(), "a própria senha");

        // O admin fixo altera tudo de um promovido
        ClienteHttp.Resposta editado = http().put("/admin/usuarios/" + idA, ClienteHttp.json("nome", "Editado Pelo Fixo",
                "email", emailAleatorio(), "telefone", telefoneAleatorio()), fixo);
        assertEquals(200, editado.status(), editado.corpo());
        assertEquals(204, http().put("/admin/usuarios/" + idA + "/senha",
                ClienteHttp.json("senha", SENHA), fixo).status(), "fixo trocando senha de admin");

        assertEquals(200, http().put("/admin/usuarios/" + idB + "/perfil",
                ClienteHttp.json("perfil", "USUARIO"), fixo).status(), "o fixo rebaixa um promovido");
        assertEquals(204, http().put("/admin/usuarios/" + idB + "/senha",
                ClienteHttp.json("senha", NOVA_SENHA), tokenA).status(), "rebaixado volta a ser usuário comum");
        assertEquals(204, http().delete("/admin/usuarios/" + idA, fixo).status(), "fixo exclui um admin promovido");
    }

    // --------------------------------------------------------- denúncias

    @Test
    void removerDenunciaRecalculaENumeroSaiQuandoFicaSemDenuncias() {
        String admin = tokenAdmin();
        String numero = telefoneAleatorio();
        String d1 = denunciar(numero, novoUsuarioToken());
        String d2 = denunciar(numero, novoUsuarioToken());

        ClienteHttp.Resposta lista = http().get("/admin/denuncias", admin);
        assertEquals(200, lista.status());
        assertTrue(lista.corpo().contains(numero), "a moderação mostra o número denunciado");

        ClienteHttp.Resposta r1 = http().delete("/admin/denuncias/" + d1, admin);
        assertEquals(200, r1.status(), r1.corpo());
        assertEquals("1", r1.campo("totalDenunciasRestantes"));
        assertEquals("false", r1.campo("numeroRemovido"));
        String golpistaId = r1.campo("golpistaId");

        ClienteHttp.Resposta r2 = http().delete("/admin/denuncias/" + d2, admin);
        assertEquals("true", r2.campo("numeroRemovido"), "sem denúncias, o número sai da base");
        assertEquals(404, http().get("/golpistas/" + golpistaId, admin).status());
        assertEquals(404, http().delete("/admin/denuncias/" + d2, admin).status(), "já removida");
    }

    @Test
    void numeroConfirmadoVoltaASuspeitoAoPerderDenuncias() {
        String admin = tokenAdmin();
        String numero = telefoneAleatorio();
        String primeira = denunciar(numero, novoUsuarioToken());
        denunciar(numero, novoUsuarioToken());
        denunciar(numero, novoUsuarioToken()); // 3 = limite de confirmação nos testes
        assertTrue(http().get("/golpistas/blacklist", null).corpo().contains(numero));

        ClienteHttp.Resposta r = http().delete("/admin/denuncias/" + primeira, admin);
        assertEquals("SUSPEITO", r.campo("statusAtual"));
        assertFalse(http().get("/golpistas/blacklist", null).corpo().contains(numero),
                "com 2 denúncias, o número sai da blacklist");
    }

    @Test
    void excluirUsuarioApagandoDenunciasRecalculaOsNumeros() {
        String admin = tokenAdmin();
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        String numero = telefoneAleatorio();
        String denuncia = denunciar(numero, reg.campo("token"));
        String golpistaId = golpistaDaDenuncia(admin, denuncia);
        assertNotNull(golpistaId);

        assertEquals(204, http().delete("/admin/usuarios/" + reg.campo("id") + "?apagarDenuncias=true", admin)
                .status());
        assertEquals(404, http().get("/golpistas/" + golpistaId, admin).status(),
                "a única denúncia era dessa pessoa: o número sai da base");
    }

    @Test
    void excluirUsuarioSemApagarMantemDenunciaAnonima() {
        String admin = tokenAdmin();
        ClienteHttp.Resposta reg = registrar(emailAleatorio(), telefoneAleatorio());
        String numero = telefoneAleatorio();
        denunciar(numero, reg.campo("token"));

        assertEquals(204, http().delete("/admin/usuarios/" + reg.campo("id"), admin).status());
        ClienteHttp.Resposta lista = http().get("/admin/denuncias", admin);
        assertTrue(lista.corpo().contains(numero), "a denúncia continua na base");
    }

    // ----------------------------------------------------------- números

    @Test
    void adminConfirmaManualmenteERemoveNumero() {
        String admin = tokenAdmin();
        String numero = telefoneAleatorio();
        String denuncia = denunciar(numero, novoUsuarioToken());
        String golpistaId = golpistaDaDenuncia(admin, denuncia);

        ClienteHttp.Resposta confirmado = http().put("/admin/numeros/" + golpistaId + "/status",
                ClienteHttp.json("status", "CONFIRMADO"), admin);
        assertEquals(200, confirmado.status(), confirmado.corpo());
        assertEquals("CONFIRMADO", confirmado.campo("status"));
        assertTrue(http().get("/golpistas/blacklist", null).corpo().contains(numero));

        assertEquals(400, http().put("/admin/numeros/" + golpistaId + "/status",
                ClienteHttp.json("status", "TALVEZ"), admin).status());

        assertEquals(204, http().delete("/admin/numeros/" + golpistaId, admin).status());
        assertEquals(404, http().get("/golpistas/" + golpistaId, admin).status());
        assertFalse(http().get("/admin/denuncias", admin).corpo().contains(numero),
                "as denúncias do número saem junto");
    }

    @Test
    void resumoTrazIndicadores() {
        ClienteHttp.Resposta r = http().get("/admin/resumo", tokenAdmin());
        assertEquals(200, r.status());
        for (String campo : new String[]{"usuarios", "administradores", "denuncias", "numeros",
                "numerosConfirmados", "limiteConfirmacao"}) {
            assertNotNull(r.campo(campo), campo);
        }
    }

    // ------------------------------------------------------------- apoio

    private ClienteHttp.Resposta login(String email, String senha) {
        return http().post("/auth/login", ClienteHttp.json("email", email, "senha", senha), null);
    }

    /** Registra uma denúncia contra o número e devolve o id dela. */
    private String denunciar(String numero, String token) {
        ClienteHttp.Resposta d = http().post("/denuncias", ClienteHttp.json("telefoneGolpista", numero,
                "canal", "WHATSAPP", "descricao", "Teste da moderação do administrador."), token);
        assertEquals(201, d.status(), d.corpo());
        return d.campo("id");
    }

    /** Id do número denunciado, lido da própria denúncia. */
    private String golpistaDaDenuncia(String admin, String denunciaId) {
        return http().get("/denuncias/" + denunciaId, admin).campo("golpistaId");
    }
}
