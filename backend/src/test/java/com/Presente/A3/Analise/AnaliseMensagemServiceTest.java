package com.Presente.A3.Analise;

import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.NivelRisco;
import com.Presente.A3.Service.GolpistaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes das camadas de análise. Sobe o Spring com o perfil "test" (banco H2
 * em memória) porque a base colaborativa agora é persistida; cada teste é
 * desfeito ao final ({@code @Transactional}).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnaliseMensagemServiceTest {

    private static final String GOLPE_TIPICO = "Parabéns! Você ganhou um brinde da Floricultura Bela Flor. "
            + "O entregador está a caminho, só precisa pagar a taxa de entrega na maquininha hoje.";

    @Autowired
    private GolpistaService golpistas;
    private final ClassificadorLocal classificador = new ClassificadorLocal();

    private AnaliseMensagemService servico(Optional<AnalisadorIA> ia) {
        return new AnaliseMensagemService(new AnalisadorRegras(), classificador, golpistas, ia, 0.5);
    }

    @Test
    void mensagemTipicaDeGolpeVaiParaAlto() {
        ResultadoVerificacao r = servico(Optional.empty()).analisar(GOLPE_TIPICO, null, "t");
        assertEquals(NivelRisco.ALTO, r.getNivelRisco());
        assertFalse(r.isInconclusivo());
    }

    @Test
    void mensagemComumVaiParaBaixo() {
        ResultadoVerificacao r = servico(Optional.empty()).analisar("Oi, vamos almoçar amanhã?", null, "t");
        assertEquals(NivelRisco.BAIXO, r.getNivelRisco());
    }

    @Test
    void mensagemCurtaDemaisEhInconclusiva() {
        ResultadoVerificacao r = servico(Optional.empty()).analisar("Oi", null, "t");
        assertTrue(r.isInconclusivo());
    }

    @Test
    void remetenteNaBlacklistSempreEhAlto() {
        Golpista g = golpistas.obterOuCriarSuspeito("11900000001", "Teste");
        for (int i = 0; i < 3; i++) {
            golpistas.registrarDenuncia(g);
        }
        AnalisadorIA iaBaixa = (m, s) -> Optional.of(new AnalisadorIA.Parecer(0, "legítima"));
        ResultadoVerificacao r = servico(Optional.of(iaBaixa)).analisar("Oi, seu presente chegou", "11900000001", "t");
        assertEquals(NivelRisco.ALTO, r.getNivelRisco());
    }

    @Test
    void iaSoEhConsultadaNaZonaDeDuvida() {
        int[] chamadas = {0};
        AnalisadorIA ia = (m, s) -> {
            chamadas[0]++;
            return Optional.of(new AnalisadorIA.Parecer(90, "x"));
        };
        AnaliseMensagemService s = servico(Optional.of(ia));
        s.analisar("Oi, vamos almoçar amanhã?", null, "t");
        s.analisar(GOLPE_TIPICO, null, "t");
        assertEquals(0, chamadas[0]);
        s.analisar("A loja informa que a taxa de entrega é grátis neste fim de semana.", null, "t");
        assertEquals(1, chamadas[0]);
    }

    @Test
    void semIaOResultadoNaoUsaACamadaIa() {
        ResultadoVerificacao r = servico(Optional.empty()).analisar(GOLPE_TIPICO, null, "t");
        assertFalse(r.getCamadas().contains("IA"));
    }

    @Test
    void mensagemVaziaLancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> servico(Optional.empty()).analisar("  ", null, "t"));
    }

    @Test
    void limitadorBloqueiaAposOTeto() {
        LimitadorUso l = new LimitadorUso(2, 10);
        assertTrue(l.tentar("a"));
        assertTrue(l.tentar("a"));
        assertFalse(l.tentar("a"));
        assertTrue(l.tentar("b"));
    }
}
