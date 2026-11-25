package com.Presente.A3.Admin;

import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Seguranca.SomenteAdmin;
import com.Presente.A3.Seguranca.UsuarioAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Área de administração ({@code /admin}). <b>Todas as rotas exigem um administrador</b>,
 * conferido no banco a cada chamada (quem perde o acesso sai na hora).
 *
 * <pre>
 *   GET    /admin/resumo                      indicadores
 *   GET    /admin/usuarios                    lista de usuários (com perfil e nº de denúncias)
 *   POST   /admin/usuarios                    cadastra usuário            → 201
 *   GET    /admin/usuarios/{id}               um usuário
 *   PUT    /admin/usuarios/{id}               edita nome, e-mail e telefone
 *   PUT    /admin/usuarios/{id}/senha         redefine a senha            → 204
 *   PUT    /admin/usuarios/{id}/perfil        promove (ADMIN) ou rebaixa (USUARIO)
 *   DELETE /admin/usuarios/{id}?apagarDenuncias=false                     → 204
 *   GET    /admin/denuncias                   denúncias com denunciante e número
 *   DELETE /admin/denuncias/{id}              remove e recalcula o número
 *   PUT    /admin/numeros/{id}/status         SUSPEITO ou CONFIRMADO
 *   DELETE /admin/numeros/{id}                remove o número e as denúncias dele → 204
 * </pre>
 */
@SomenteAdmin
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
    }

    @GetMapping("/resumo")
    public AdminDtos.Resumo resumo() {
        return admin.resumo();
    }

    // ---- usuários

    @GetMapping("/usuarios")
    public List<AdminDtos.Usuario> usuarios() {
        return admin.listarUsuarios();
    }

    @GetMapping("/usuarios/{id}")
    public AdminDtos.Usuario usuario(@PathVariable("id") Long id) {
        return admin.buscarUsuario(id);
    }

    @PostMapping("/usuarios")
    public ResponseEntity<AdminDtos.Usuario> criarUsuario(@RequestBody AdminDtos.NovoUsuario req,
                                                          HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.criarUsuario(req, quem(http)));
    }

    @PutMapping("/usuarios/{id}")
    public AdminDtos.Usuario editarUsuario(@PathVariable("id") Long id, @RequestBody AdminDtos.EdicaoUsuario req,
                                           HttpServletRequest http) {
        return admin.editarUsuario(id, req, quem(http));
    }

    @PutMapping("/usuarios/{id}/senha")
    public ResponseEntity<Void> redefinirSenha(@PathVariable("id") Long id, @RequestBody AdminDtos.NovaSenha req,
                                               HttpServletRequest http) {
        admin.redefinirSenha(id, req, quem(http));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/usuarios/{id}/perfil")
    public AdminDtos.Usuario alterarPerfil(@PathVariable("id") Long id, @RequestBody AdminDtos.NovoPerfil req,
                                           HttpServletRequest http) {
        return admin.alterarPerfil(id, req, quem(http));
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> excluirUsuario(@PathVariable("id") Long id,
                                               @RequestParam(value = "apagarDenuncias", defaultValue = "false")
                                               boolean apagarDenuncias,
                                               HttpServletRequest http) {
        admin.excluirUsuario(id, apagarDenuncias, quem(http));
        return ResponseEntity.noContent().build();
    }

    // ---- denúncias

    @GetMapping("/denuncias")
    public List<AdminDtos.Denuncia> denuncias() {
        return admin.listarDenuncias();
    }

    @DeleteMapping("/denuncias/{id}")
    public AdminDtos.RemocaoDenuncia removerDenuncia(@PathVariable("id") Long id) {
        return admin.removerDenuncia(id);
    }

    // ---- números

    @PutMapping("/numeros/{id}/status")
    public Golpista alterarStatusNumero(@PathVariable("id") Long id, @RequestBody AdminDtos.NovoStatus req) {
        return admin.alterarStatusNumero(id, req);
    }

    @DeleteMapping("/numeros/{id}")
    public ResponseEntity<Void> removerNumero(@PathVariable("id") Long id) {
        admin.removerNumero(id);
        return ResponseEntity.noContent().build();
    }

    private static Long quem(HttpServletRequest http) {
        return UsuarioAutenticado.de(http).clienteId();
    }
}
