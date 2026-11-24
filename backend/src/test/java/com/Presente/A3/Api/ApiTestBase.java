package com.Presente.A3.Api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Base dos testes de API: sobe a aplicação inteira (servidor embutido em porta
 * aleatória, banco H2 em memória) e oferece atalhos para criar usuários.
 *
 * <p>Todas as subclasses compartilham o mesmo contexto do Spring, então a
 * aplicação sobe uma única vez para todos os testes de API.</p>
 *
 * <p>Os dados criados nos testes usam e-mails e telefones aleatórios, pois o
 * banco é compartilhado entre as classes de teste e nada é desfeito ao final.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiTestBase {

    /** E-mail configurado como ADMIN em application-test.properties. */
    static final String EMAIL_ADMIN = "admin@teste.com";
    static final String SENHA = "senha-forte-123";

    /** Porta escolhida pelo Spring; lida pela propriedade para não depender de anotações da versão. */
    @Value("${local.server.port}")
    private int porta;

    private ClienteHttp http;

    ClienteHttp http() {
        if (http == null) {
            http = new ClienteHttp(porta);
        }
        return http;
    }

    /** Telefone brasileiro de celular, aleatório (11 dígitos), para não colidir entre testes. */
    static String telefoneAleatorio() {
        return "119" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L);
    }

    static String emailAleatorio() {
        return "u" + ThreadLocalRandom.current().nextLong(1_000_000_000L) + System.nanoTime() + "@teste.com";
    }

    /** Cria uma conta e devolve a resposta do registro (token + cliente). */
    ClienteHttp.Resposta registrar(String email, String telefone) {
        return http().post("/auth/registro",
                ClienteHttp.json("nome", "Pessoa de Teste", "email", email, "telefone", telefone, "senha", SENHA), null);
    }

    /** Cria uma conta nova e devolve só o token. */
    String novoUsuarioToken() {
        ClienteHttp.Resposta r = registrar(emailAleatorio(), telefoneAleatorio());
        if (r.status() != 201) {
            throw new IllegalStateException("Registro falhou: " + r.status() + " " + r.corpo());
        }
        return r.campo("token");
    }

    /** Token do administrador: faz login e, se a conta ainda não existe, cria. */
    String tokenAdmin() {
        ClienteHttp.Resposta login = http().post("/auth/login",
                ClienteHttp.json("email", EMAIL_ADMIN, "senha", SENHA), null);
        if (login.status() == 200) {
            return login.campo("token");
        }
        ClienteHttp.Resposta reg = registrar(EMAIL_ADMIN, telefoneAleatorio());
        if (reg.status() != 201) {
            throw new IllegalStateException("Não foi possível criar o admin de teste: " + reg.corpo());
        }
        return reg.campo("token");
    }
}
