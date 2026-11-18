package com.Presente.A3.Service;

import com.Presente.A3.Dto.DenunciaRequest;
import com.Presente.A3.Modelo.CanalContato;
import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fluxo de denúncia gravando no banco (H2 em memória no perfil "test"). */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DenunciaServiceTest {

    private static final String NUMERO = "(11) 95555-0001";

    @Autowired
    private ClienteService clientes;
    @Autowired
    private GolpistaService golpistas;
    @Autowired
    private DenunciaService denuncias;

    private Long novoCliente(String nome, String telefone) {
        return clientes.save(new Cliente(null, nome, nome + "@example.com", telefone)).getId();
    }

    private Denuncia denunciar(Long clienteId, String telefone) {
        DenunciaRequest req = new DenunciaRequest();
        req.setClienteId(clienteId);
        req.setTelefoneGolpista(telefone);
        req.setCanal(CanalContato.WHATSAPP);
        req.setDescricao("Disse que ganhei um brinde e cobrou uma taxa na maquininha do entregador.");
        return denuncias.registrar(req);
    }

    @Test
    void denunciaEhGravadaComIdDoBanco() {
        Denuncia d = denunciar(novoCliente("a", "11911110001"), NUMERO);
        assertNotNull(d.getId());
        assertTrue(denuncias.buscarPorId(d.getId()).isPresent());
        Golpista g = golpistas.findByTelefone(NUMERO).orElseThrow();
        assertEquals(1, g.getTotalDenuncias());
        assertEquals(StatusGolpista.SUSPEITO, g.getStatus());
    }

    @Test
    void tresPessoasDistintasConfirmamOGolpista() {
        denunciar(novoCliente("a", "11911110001"), NUMERO);
        denunciar(novoCliente("b", "11911110002"), NUMERO);
        denunciar(novoCliente("c", "11911110003"), NUMERO);

        Golpista g = golpistas.findByTelefone(NUMERO).orElseThrow();
        assertEquals(3, g.getTotalDenuncias());
        assertEquals(StatusGolpista.CONFIRMADO, g.getStatus());
        assertTrue(golpistas.listarBlacklist().stream().anyMatch(x -> x.getId().equals(g.getId())));
    }

    @Test
    void mesmaPessoaNaoDenunciaDuasVezes() {
        Long cliente = novoCliente("a", "11911110001");
        denunciar(cliente, NUMERO);
        assertThrows(IllegalStateException.class, () -> denunciar(cliente, "+55 11 95555-0001"));
    }

    @Test
    void naoPodeDenunciarOProprioNumero() {
        Long cliente = novoCliente("a", "11955550001");
        assertThrows(IllegalArgumentException.class, () -> denunciar(cliente, NUMERO));
    }

    @Test
    void golpistaDuplicadoPorTelefoneEhRecusado() {
        Golpista g = new Golpista(null, "X", null, NUMERO);
        golpistas.save(g);
        assertThrows(IllegalStateException.class,
                () -> golpistas.save(new Golpista(null, "Y", null, "11955550001")));
    }
}
