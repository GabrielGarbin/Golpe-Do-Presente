package com.Presente.A3.Controller;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Seguranca.Publico;
import com.Presente.A3.Seguranca.SomenteAdmin;
import com.Presente.A3.Service.GolpistaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * Base de golpistas ({@code /golpistas}).
 * <ul>
 *   <li>{@code GET /golpistas/blacklist}: público, só números CONFIRMADOS;</li>
 *   <li>leitura da lista completa (inclui suspeitos): exige login;</li>
 *   <li>criar, alterar e apagar: somente administradores.</li>
 * </ul>
 */
@RestController
@RequestMapping("/golpistas")
public class GolpistaController {

    @Autowired
    private GolpistaService golpistaService;

    // READ - Listar todos os golpistas
    @GetMapping
    public List<Golpista> getAllGolpistas() {
        return golpistaService.findAll();
    }

    // READ - Blacklist pública: apenas números CONFIRMADOS, dos mais denunciados para os menos
    @Publico
    @GetMapping("/blacklist")
    public List<Golpista> getBlacklist() {
        return golpistaService.listarBlacklist();
    }

    // READ - Buscar um golpista por ID
    @GetMapping("/{id}")
    public ResponseEntity<Golpista> getGolpistaById(@PathVariable Long id) {
        Optional<Golpista> golpista = golpistaService.findById(id);
        return golpista.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // CREATE - Criar um novo golpista
    @SomenteAdmin
    @PostMapping
    public Golpista createGolpista(@RequestBody Golpista golpista) {
        return golpistaService.save(golpista);
    }

    // UPDATE - Atualizar um golpista por ID
    @SomenteAdmin
    @PutMapping("/{id}")
    public ResponseEntity<Golpista> updateGolpista(@PathVariable Long id, @RequestBody Golpista golpistaAtualizado) {
        Optional<Golpista> golpista = golpistaService.update(id, golpistaAtualizado);
        return golpista.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // DELETE - Deletar um golpista por ID
    @SomenteAdmin
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGolpista(@PathVariable Long id) {
        if (golpistaService.deleteById(id)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}



