package com.Presente.A3.Controller;

import com.Presente.A3.Analise.ConsultaCnpjService;
import com.Presente.A3.Dto.MensagemRequest;
import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Service.PrevencaoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de <b>prevenção</b> ({@code /prevencao}): o que a pessoa usa
 * quando recebe a mensagem, antes de o "entregador" chegar.
 */
@RestController
@RequestMapping("/prevencao")
public class PrevencaoController {

    private final PrevencaoService prevencaoService;
    private final ConsultaCnpjService consultaCnpj;

    public PrevencaoController(PrevencaoService prevencaoService, ConsultaCnpjService consultaCnpj) {
        this.prevencaoService = prevencaoService;
        this.consultaCnpj = consultaCnpj;
    }

    /**
     * Verifica a loja pelo CNPJ: {@code GET /prevencao/cnpj?numero=00.000.000/0001-91}.
     * Consulta a Receita via BrasilAPI; se estiver fora do ar, devolve resultado inconclusivo.
     */
    @GetMapping("/cnpj")
    public ResultadoVerificacao verificarCnpj(@RequestParam String numero, HttpServletRequest http) {
        return consultaCnpj.verificar(numero, http.getRemoteAddr());
    }

    /**
     * Consulta um número: {@code GET /prevencao/telefone?numero=11987654321}.
     * O número vai como parâmetro de consulta (e não no caminho) para aceitar
     * qualquer formatação, como "+55 (11) 98765-4321".
     */
    @GetMapping("/telefone")
    public ResultadoVerificacao verificarTelefone(@RequestParam String numero) {
        return prevencaoService.verificarTelefone(numero);
    }

    /**
     * Analisa o texto de uma mensagem: {@code POST /prevencao/mensagem}.
     * O campo {@code telefoneRemetente} é opcional e, se enviado, cruza o
     * resultado com a base colaborativa.
     */
    @PostMapping("/mensagem")
    public ResultadoVerificacao analisarMensagem(@RequestBody MensagemRequest request,
                                                 HttpServletRequest http) {
        return prevencaoService.analisarMensagem(
                request.getMensagem(), request.getTelefoneRemetente(), http.getRemoteAddr());
    }

    /**
     * {@code GET /prevencao/camadas} - quais camadas de análise estão ativas
     * (útil para a demonstração e para conferir se a IA opcional está ligada).
     */
    @GetMapping("/camadas")
    public Map<String, Object> camadas() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("regras", true);
        m.put("classificadorLocal", true);
        m.put("baseColaborativa", true);
        m.put("consultaCnpj", true);
        m.put("ia", prevencaoService.iaDisponivel());
        return m;
    }

    /** {@code GET /prevencao/checklist} - o que fazer antes, durante e depois da entrega. */
    @GetMapping("/checklist")
    public Map<String, List<String>> checklist() {
        return prevencaoService.checklistEntrega();
    }
}
