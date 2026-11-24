package com.Presente.A3.Controller;

import com.Presente.A3.Seguranca.Publico;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Verificação de saúde ({@code GET /saude}): responde {@code {"status":"UP"}}
 * sem tocar no banco. Plataformas de nuvem, o Docker e o monitoramento usam
 * esse endereço para saber se a aplicação está viva.
 */
@RestController
@Publico
public class SaudeController {

    @GetMapping("/saude")
    public Map<String, String> saude() {
        return Map.of("status", "UP");
    }
}
