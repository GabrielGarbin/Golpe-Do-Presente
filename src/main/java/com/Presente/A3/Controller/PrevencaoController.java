package com.Presente.A3.Controller;

import com.Presente.A3.Dto.MensagemRequest;
import com.Presente.A3.Dto.ResultadoVerificacao;
import com.Presente.A3.Service.PrevencaoService;
import org.springframework.web.bind.annotation.*;

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

    public PrevencaoController(PrevencaoService prevencaoService) {
        this.prevencaoService = prevencaoService;
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
    public ResultadoVerificacao analisarMensagem(@RequestBody MensagemRequest request) {
        return prevencaoService.analisarMensagem(
                request.getMensagem(), request.getTelefoneRemetente());
    }

    /** {@code GET /prevencao/checklist} - o que fazer antes, durante e depois da entrega. */
    @GetMapping("/checklist")
    public Map<String, List<String>> checklist() {
        return prevencaoService.checklistEntrega();
    }
}
