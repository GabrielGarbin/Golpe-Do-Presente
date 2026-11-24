package com.Presente.A3.Controller;

import com.Presente.A3.Dto.DenunciaRequest;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Seguranca.Publico;
import com.Presente.A3.Seguranca.SomenteAdmin;
import com.Presente.A3.Seguranca.UsuarioAutenticado;
import com.Presente.A3.Service.DenunciaService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Endpoints da denúncia colaborativa ({@code /denuncias}). */
@RestController
@RequestMapping("/denuncias")
public class DenunciaController {

    private final DenunciaService denunciaService;

    public DenunciaController(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    /**
     * Registra uma denúncia: {@code POST /denuncias}.
     *
     * <p>Exige login. O denunciante é SEMPRE quem está no token: um
     * {@code clienteId} enviado no corpo é ignorado, o que impede alguém de
     * denunciar em nome de outra pessoa.</p>
     *
     * <p>Respostas: 201 (criada), 400 (dados inválidos), 401 (sem login),
     * 404 (cliente inexistente) e 409 (você já denunciou este número).</p>
     */
    @PostMapping
    public ResponseEntity<Denuncia> registrar(@RequestBody DenunciaRequest request,
                                              HttpServletRequest http) {
        request.setClienteId(UsuarioAutenticado.de(http).clienteId());
        return ResponseEntity.status(HttpStatus.CREATED).body(denunciaService.registrar(request));
    }

    /** {@code GET /denuncias} - lista todas as denúncias (traz o id de quem denunciou: só admin). */
    @SomenteAdmin
    @GetMapping
    public List<Denuncia> listar() {
        return denunciaService.listar();
    }

    /** {@code GET /denuncias/estatisticas} - indicadores agregados, sem dados pessoais (público). */
    @Publico
    @GetMapping("/estatisticas")
    public Map<String, Object> estatisticas() {
        return denunciaService.estatisticas();
    }

    /** {@code GET /denuncias/{id}} - 200 ou 404 (só admin). */
    @SomenteAdmin
    @GetMapping("/{id}")
    public ResponseEntity<Denuncia> buscarPorId(@PathVariable Long id) {
        return denunciaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
