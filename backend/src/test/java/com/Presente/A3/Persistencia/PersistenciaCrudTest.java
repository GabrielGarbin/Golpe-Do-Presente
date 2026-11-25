package com.Presente.A3.Persistencia;

import com.Presente.A3.Dto.DenunciaRequest;
import com.Presente.A3.Modelo.CanalContato;
import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Modelo.CredencialSenha;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import com.Presente.A3.Repositorio.CredencialSenhaRepository;
import com.Presente.A3.Repositorio.DenunciaRepository;
import com.Presente.A3.Seguranca.AuthService;
import com.Presente.A3.Seguranca.RegistroRequest;
import com.Presente.A3.Seguranca.RespostaAuth;
import com.Presente.A3.Service.ClienteService;
import com.Presente.A3.Service.DenunciaService;
import com.Presente.A3.Service.GolpistaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de persistência: CRUD dos dados importantes (clientes, golpistas,
 * denúncias, credenciais) e verificação de integridade e consistência no banco
 * (H2 em memória no perfil "test"). Cada teste é desfeito ao terminar
 * ({@code @Transactional}), então não deixa dados para os outros.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:application-test.properties") // vence backend/config: nunca usar o banco real
@Transactional
class PersistenciaCrudTest {

    @Autowired
    private ClienteService clientes;
    @Autowired
    private GolpistaService golpistas;
    @Autowired
    private DenunciaService denuncias;
    @Autowired
    private DenunciaRepository denunciaRepo;
    @Autowired
    private CredencialSenhaRepository credenciaisRepo;
    @Autowired
    private AuthService auth;

    private static String telefone() {
        return "118" + ThreadLocalRandom.current().nextLong(10_000_000L, 100_000_000L);
    }

    private Long novoCliente(String nome) {
        return clientes.save(new Cliente(null, nome, nome + ThreadLocalRandom.current().nextInt() + "@teste.com", telefone())).getId();
    }

    private Denuncia denunciar(Long clienteId, String telefoneGolpista) {
        DenunciaRequest req = new DenunciaRequest();
        req.setClienteId(clienteId);
        req.setTelefoneGolpista(telefoneGolpista);
        req.setCanal(CanalContato.WHATSAPP);
        req.setDescricao("Disse que ganhei um brinde e cobrou uma taxa na maquininha do entregador.");
        return denuncias.registrar(req);
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    void clienteCriaLeAtualizaEExclui() {
        Cliente c = clientes.save(new Cliente(null, "Ana", "ana.crud@teste.com", telefone()));
        assertNotNull(c.getId(), "o id é gerado pelo banco");

        assertEquals("Ana", clientes.findById(c.getId()).orElseThrow().getNome());

        Cliente alterado = new Cliente(null, "Ana Souza", "ana.crud@teste.com", c.getTelefone());
        assertEquals("Ana Souza", clientes.update(c.getId(), alterado).orElseThrow().getNome());
        assertEquals("Ana Souza", clientes.findById(c.getId()).orElseThrow().getNome());

        assertTrue(clientes.deleteById(c.getId()));
        assertTrue(clientes.findById(c.getId()).isEmpty());
        assertFalse(clientes.deleteById(c.getId()), "apagar de novo não encontra nada");
    }

    @Test
    void golpistaCriaLeAtualizaEExcluiComTelefoneNormalizado() {
        String numero = telefone();
        Golpista g = golpistas.save(new Golpista(null, "Falso Entregador", null, "(" + numero.substring(0, 2) + ") "
                + numero.substring(2, 7) + "-" + numero.substring(7)));

        assertEquals(numero, g.getTelefone(), "guardado só com dígitos");
        assertEquals(StatusGolpista.SUSPEITO, g.getStatus());
        assertEquals(0, g.getTotalDenuncias());
        assertTrue(golpistas.findByTelefone("+55 " + numero).isPresent(), "achado por qualquer formatação");

        Golpista novo = new Golpista(null, "Outro Nome", null, numero);
        assertEquals("Outro Nome", golpistas.update(g.getId(), novo).orElseThrow().getNome());

        assertThrows(IllegalStateException.class, () -> golpistas.save(new Golpista(null, "Repetido", null, numero)));

        assertTrue(golpistas.deleteById(g.getId()));
        assertTrue(golpistas.findById(g.getId()).isEmpty());
    }

    @Test
    void denunciaEhGravadaEPodeSerLida() {
        Denuncia d = denunciar(novoCliente("a"), telefone());

        assertNotNull(d.getId());
        assertTrue(denuncias.buscarPorId(d.getId()).isPresent());
        assertTrue(denuncias.listar().stream().anyMatch(x -> x.getId().equals(d.getId())));
    }

    // ------------------------------------------------------------------ integridade e consistência

    @Test
    void contadorDoGolpistaFicaConsistenteComOsRegistrosDeDenuncia() {
        String alvo = telefone();
        denunciar(novoCliente("a"), alvo);
        denunciar(novoCliente("b"), alvo);

        Golpista g = golpistas.findByTelefone(alvo).orElseThrow();
        List<Denuncia> registros = denunciaRepo.findByGolpistaIdOrderByIdAsc(g.getId());

        assertEquals(2, registros.size());
        assertEquals(registros.size(), g.getTotalDenuncias(), "contador = nº de denúncias gravadas");
        assertEquals(StatusGolpista.SUSPEITO, g.getStatus(), "2 de 3: ainda não confirmado");

        denunciar(novoCliente("c"), alvo);
        g = golpistas.findByTelefone(alvo).orElseThrow();
        assertEquals(3, g.getTotalDenuncias());
        assertEquals(StatusGolpista.CONFIRMADO, g.getStatus());
        Long idGolpista = g.getId();

assertTrue(golpistas.listarBlacklist().stream()
    .anyMatch(x -> x.getId().equals(idGolpista)));
        
    }

    @Test
    void terceiraDenunciaDaMesmaPessoaEhBarradaENaoAlteraAContagem() {
        Long cliente = novoCliente("a");
        String alvo = telefone();
        denunciar(cliente, alvo);
        denunciar(cliente, alvo); // a 2ª é permitida

        assertThrows(IllegalStateException.class, () -> denunciar(cliente, alvo));

        assertEquals(2, golpistas.findByTelefone(alvo).orElseThrow().getTotalDenuncias());
    }

    @Test
    void obancoRecusaDenunciaDuplicadaMesmoSeAServiceForContornada() {
        Long cliente = novoCliente("a");
        String alvo = telefone();
        Denuncia primeira = denunciar(cliente, alvo);

        Denuncia duplicada = new Denuncia();
        duplicada.setClienteId(cliente);
        duplicada.setGolpistaId(primeira.getGolpistaId());
        duplicada.setCanal(CanalContato.SMS);
        duplicada.setData(LocalDateTime.now());

        // Gravando direto no repositório (sem passar pelas regras do serviço) na mesma
        // vaga (ordem 1): a restrição UNIQUE (cliente_id, golpista_id, ordem) precisa barrar.
        assertThrows(DataIntegrityViolationException.class, () -> denunciaRepo.saveAndFlush(duplicada));
    }

    @Test
    void denunciaDeClienteInexistenteNaoGravaNada() {
        long antes = denunciaRepo.count();

        assertThrows(RuntimeException.class, () -> denunciar(999_999_999L, telefone()));

        assertEquals(antes, denunciaRepo.count());
    }

    // ------------------------------------------------------------------ credenciais

    @Test
    void registroGravaClienteEHashDaSenhaMasNuncaASenha() {
        RegistroRequest req = new RegistroRequest();
        req.setNome("Pessoa Persistida");
        req.setEmail("persistida" + ThreadLocalRandom.current().nextInt(1_000_000) + "@teste.com");
        req.setTelefone(telefone());
        req.setSenha("senha-bem-secreta-99");

        RespostaAuth resposta = auth.registrar(req);
        Long id = resposta.getCliente().getId();

        assertTrue(clientes.findById(id).isPresent());
        CredencialSenha credencial = credenciaisRepo.findById(id).orElseThrow();
        assertTrue(credencial.getSenhaHash().startsWith("pbkdf2-sha256$"));
        assertFalse(credencial.getSenhaHash().contains("senha-bem-secreta-99"));
        assertNotNull(credencial.getCriadoEm());
    }
}
