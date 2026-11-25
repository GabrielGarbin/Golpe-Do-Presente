package com.Presente.A3.Admin;

import com.Presente.A3.Modelo.Administrador;
import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Modelo.CredencialSenha;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import com.Presente.A3.Repositorio.AdministradorRepository;
import com.Presente.A3.Repositorio.ClienteRepository;
import com.Presente.A3.Repositorio.CredencialSenhaRepository;
import com.Presente.A3.Repositorio.DenunciaRepository;
import com.Presente.A3.Seguranca.ConflitoException;
import com.Presente.A3.Seguranca.DadosInvalidosException;
import com.Presente.A3.Seguranca.Perfil;
import com.Presente.A3.Seguranca.PerfilService;
import com.Presente.A3.Seguranca.SenhaService;
import com.Presente.A3.Seguranca.ValidacaoCadastro;
import com.Presente.A3.Service.GolpistaService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras da área de administração.
 *
 * <ul>
 *   <li><b>Usuários</b>: listar, cadastrar, editar, redefinir senha, promover/rebaixar
 *       e excluir (mantendo as denúncias como anônimas ou apagando-as junto).</li>
 *   <li><b>Denúncias</b>: listar com quem denunciou e qual número, e remover. Ao
 *       remover, o contador e o status do número são recalculados.</li>
 *   <li><b>Números</b>: confirmar manualmente, voltar a suspeito ou remover da base.</li>
 * </ul>
 *
 * <p><b>Travas de segurança</b>: o administrador não pode se excluir nem se rebaixar
 * (assim sempre resta pelo menos um), e os administradores fixos de
 * {@code ADMINS_EMAIL} não podem ser rebaixados, excluídos nem ter o e-mail trocado
 * pelo painel.</p>
 */
@Service
public class AdminService {

    private final ClienteRepository clientes;
    private final CredencialSenhaRepository credenciais;
    private final AdministradorRepository administradores;
    private final DenunciaRepository denuncias;
    private final GolpistaService golpistas;
    private final SenhaService senhas;
    private final PerfilService perfis;

    public AdminService(ClienteRepository clientes, CredencialSenhaRepository credenciais,
                        AdministradorRepository administradores, DenunciaRepository denuncias,
                        GolpistaService golpistas, SenhaService senhas, PerfilService perfis) {
        this.clientes = clientes;
        this.credenciais = credenciais;
        this.administradores = administradores;
        this.denuncias = denuncias;
        this.golpistas = golpistas;
        this.senhas = senhas;
        this.perfis = perfis;
    }

    // ------------------------------------------------------------------ resumo

    @Transactional(readOnly = true)
    public AdminDtos.Resumo resumo() {
        List<AdminDtos.Usuario> usuarios = listarUsuarios();
        long admins = usuarios.stream().filter(u -> Perfil.ADMIN.name().equals(u.perfil())).count();
        return new AdminDtos.Resumo(
                usuarios.size(), admins,
                denuncias.count(), denuncias.countByDataAfter(LocalDateTime.now().minusDays(7)),
                golpistas.count(), golpistas.countConfirmados(), golpistas.getLimiteConfirmacao());
    }

    // ---------------------------------------------------------------- usuários

    @Transactional(readOnly = true)
    public List<AdminDtos.Usuario> listarUsuarios() {
        Set<Long> promovidos = perfis.idsPromovidos();
        Set<Long> comSenha = credenciais.findAll().stream()
                .map(CredencialSenha::getClienteId).collect(Collectors.toSet());
        Map<Long, Long> totais = new HashMap<>();
        for (Object[] linha : denuncias.contarPorCliente()) {
            totais.put(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
        }
        return clientes.findAll(Sort.by("id")).stream()
                .map(c -> paraUsuario(c, promovidos.contains(c.getId()), comSenha.contains(c.getId()),
                        totais.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.Usuario buscarUsuario(Long id) {
        Cliente c = cliente(id);
        return paraUsuario(c, perfis.ehPromovido(id), credenciais.existsById(id),
                denuncias.findByClienteId(id).size());
    }

    /**
     * @throws DadosInvalidosException dados inválidos
     * @throws ConflitoException       e-mail/telefone em uso, ou promoção indisponível
     */
    @Transactional
    public AdminDtos.Usuario criarUsuario(AdminDtos.NovoUsuario req, Long adminId) {
        if (req == null) {
            throw new DadosInvalidosException("Corpo da requisição é obrigatório.");
        }
        String nome = ValidacaoCadastro.nome(req.nome());
        String email = ValidacaoCadastro.email(req.email());
        String telefone = ValidacaoCadastro.telefone(req.telefone());
        String senha = ValidacaoCadastro.senha(req.senha());
        boolean comoAdmin = perfilPedido(req.perfil()) == Perfil.ADMIN;
        if (comoAdmin && !perfis.ehAdminFixo(email)) {
            exigirPromocaoDisponivel();
        }
        if (clientes.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe uma conta com este e-mail.");
        }
        if (clientes.existsByTelefone(telefone)) {
            throw new ConflitoException("Já existe uma conta com este telefone.");
        }

        Cliente c = clientes.save(new Cliente(null, nome, email, telefone));
        credenciais.save(new CredencialSenha(c.getId(), senhas.gerarHash(senha)));
        boolean promovido = false;
        if (comoAdmin && !perfis.ehAdminFixo(email)) {
            administradores.save(new Administrador(c.getId(), adminId));
            promovido = true;
        }
        return paraUsuario(c, promovido, true, 0);
    }

    @Transactional
    public AdminDtos.Usuario editarUsuario(Long id, AdminDtos.EdicaoUsuario req, Long adminId) {
        if (req == null) {
            throw new DadosInvalidosException("Corpo da requisição é obrigatório.");
        }
        Cliente c = cliente(id);
        protegerOutroAdmin(c, adminId);
        String nome = ValidacaoCadastro.nome(req.nome());
        String email = ValidacaoCadastro.email(req.email());
        String telefone = ValidacaoCadastro.telefone(req.telefone());

        boolean mudouEmail = !email.equalsIgnoreCase(c.getEmail() == null ? "" : c.getEmail());
        if (mudouEmail && perfis.ehAdminFixo(c.getEmail())) {
            throw new ConflitoException("O e-mail de um administrador fixo (ADMINS_EMAIL) não pode ser "
                    + "alterado pelo painel: ele perderia o acesso de administrador.");
        }
        if (clientes.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new ConflitoException("Já existe outra conta com este e-mail.");
        }
        if (clientes.existsByTelefoneAndIdNot(telefone, id)) {
            throw new ConflitoException("Já existe outra conta com este telefone.");
        }
        c.setNome(nome);
        c.setEmail(email);
        c.setTelefone(telefone);
        c = clientes.save(c);
        return paraUsuario(c, perfis.ehPromovido(id), credenciais.existsById(id),
                denuncias.findByClienteId(id).size());
    }

    /** Define uma nova senha (também serve para contas antigas, criadas antes do login com senha). */
    @Transactional
    public void redefinirSenha(Long id, AdminDtos.NovaSenha req, Long adminId) {
        protegerOutroAdmin(cliente(id), adminId);
        String senha = ValidacaoCadastro.senha(req == null ? null : req.senha());
        String hash = senhas.gerarHash(senha);
        Optional<CredencialSenha> atual = credenciais.findById(id);
        if (atual.isPresent()) {
            atual.get().setSenhaHash(hash);
            credenciais.save(atual.get());
        } else {
            credenciais.save(new CredencialSenha(id, hash));
        }
    }

    @Transactional
    public AdminDtos.Usuario alterarPerfil(Long id, AdminDtos.NovoPerfil req, Long adminId) {
        Perfil novo = perfilPedido(req == null ? null : req.perfil());
        Cliente c = cliente(id);
        if (perfis.ehAdminFixo(c.getEmail())) {
            throw new ConflitoException("Este é um administrador fixo (ADMINS_EMAIL): o perfil dele "
                    + "só muda na configuração do servidor.");
        }
        if (id.equals(adminId) && novo == Perfil.USUARIO) {
            throw new ConflitoException("Você não pode remover o seu próprio acesso de administrador.");
        }
        if (!id.equals(adminId) && perfis.ehPromovido(id) && !chamadorEhAdminFixo(adminId)) {
            throw new ConflitoException("Só o administrador principal pode alterar outro administrador.");
        }
        exigirPromocaoDisponivel();
        boolean promovido = perfis.ehPromovido(id);
        if (novo == Perfil.ADMIN && !promovido) {
            administradores.save(new Administrador(id, adminId));
            promovido = true;
        } else if (novo == Perfil.USUARIO && promovido) {
            administradores.deleteById(id);
            promovido = false;
        }
        return paraUsuario(c, promovido, credenciais.existsById(id), denuncias.findByClienteId(id).size());
    }

    /**
     * Exclui a conta.
     *
     * @param apagarDenuncias true: apaga também as denúncias da pessoa (e recalcula os
     *                        números afetados); false: as denúncias ficam, como anônimas
     */
    @Transactional
    public void excluirUsuario(Long id, boolean apagarDenuncias, Long adminId) {
        Cliente c = cliente(id);
        if (id.equals(adminId)) {
            throw new ConflitoException("Você não pode excluir a sua própria conta pelo painel.");
        }
        protegerOutroAdmin(c, adminId);
        if (perfis.ehAdminFixo(c.getEmail())) {
            throw new ConflitoException("Este é um administrador fixo (ADMINS_EMAIL) e não pode ser excluído "
                    + "pelo painel.");
        }

        if (apagarDenuncias) {
            List<Denuncia> dele = denuncias.findByClienteId(id);
            Set<Long> numerosAfetados = dele.stream().map(Denuncia::getGolpistaId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            denuncias.deleteAll(dele);
            denuncias.flush();
            numerosAfetados.forEach(g -> golpistas.recalcular(g, denuncias.findByGolpistaIdOrderByIdAsc(g)));
        } else {
            denuncias.desvincularCliente(id);
        }

        if (perfis.ehPromovido(id)) {
            administradores.deleteById(id);
        }
        if (credenciais.existsById(id)) {
            credenciais.deleteById(id);
        }
        clientes.deleteById(id);
    }

    /**
     * Regra: só o administrador fixo (ADMINS_EMAIL) mexe nos dados, na senha e na conta
     * de outro administrador. Um admin promovido altera usuários comuns e a si mesmo;
     * tentar mexer em outro admin dá 409.
     */
    private void protegerOutroAdmin(Cliente alvo, Long adminId) {
        if (!alvo.getId().equals(adminId) && perfis.perfilDe(alvo) == Perfil.ADMIN
                && !chamadorEhAdminFixo(adminId)) {
            throw new ConflitoException("Só o administrador principal pode alterar outro administrador.");
        }
    }

    private boolean chamadorEhAdminFixo(Long adminId) {
        return adminId != null && clientes.findById(adminId).map(a -> perfis.ehAdminFixo(a.getEmail())).orElse(false);
    }

    // --------------------------------------------------------------- denúncias

    @Transactional(readOnly = true)
    public List<AdminDtos.Denuncia> listarDenuncias() {
        Map<Long, Cliente> porCliente = clientes.findAll().stream()
                .collect(Collectors.toMap(Cliente::getId, Function.identity()));
        Map<Long, Golpista> porNumero = golpistas.findAll().stream()
                .collect(Collectors.toMap(Golpista::getId, Function.identity()));
        return denuncias.findAllByOrderByDataDescIdDesc().stream().map(d -> {
            Cliente c = d.getClienteId() == null ? null : porCliente.get(d.getClienteId());
            Golpista g = porNumero.get(d.getGolpistaId());
            return new AdminDtos.Denuncia(d.getId(), d.getData(),
                    d.getCanal() == null ? null : d.getCanal().name(),
                    d.getEstabelecimentoFalsificado(), d.getDescricao(),
                    d.getClienteId(), c == null ? null : c.getNome(), c == null ? null : c.getEmail(),
                    d.getGolpistaId(), g == null ? null : g.getTelefone(),
                    g == null ? null : g.getStatus().name(), g == null ? null : g.getTotalDenuncias());
        }).toList();
    }

    /** Remove a denúncia e recalcula o número denunciado (que sai da base se ficar sem denúncias). */
    @Transactional
    public AdminDtos.RemocaoDenuncia removerDenuncia(Long id) {
        Denuncia d = denuncias.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Denúncia não encontrada: " + id));
        Long golpistaId = d.getGolpistaId();
        denuncias.delete(d);
        denuncias.flush();
        Optional<Golpista> g = golpistas.recalcular(golpistaId, denuncias.findByGolpistaIdOrderByIdAsc(golpistaId));
        return new AdminDtos.RemocaoDenuncia(id, golpistaId, g.isEmpty(),
                g.map(Golpista::getTotalDenuncias).orElse(0),
                g.map(x -> x.getStatus().name()).orElse(null));
    }

    // ----------------------------------------------------------------- números

    @Transactional
    public Golpista alterarStatusNumero(Long id, AdminDtos.NovoStatus req) {
        String valor = req == null || req.status() == null ? "" : req.status().trim().toUpperCase(Locale.ROOT);
        StatusGolpista status;
        try {
            status = StatusGolpista.valueOf(valor);
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException("Status inválido: use SUSPEITO ou CONFIRMADO.");
        }
        return golpistas.alterarStatus(id, status);
    }

    /** Remove o número da base junto com todas as denúncias contra ele. */
    @Transactional
    public void removerNumero(Long id) {
        if (golpistas.findById(id).isEmpty()) {
            throw new NoSuchElementException("Número não encontrado: " + id);
        }
        denuncias.apagarDoGolpista(id);
        golpistas.deleteById(id);
    }

    // ------------------------------------------------------------------- apoio

    private Cliente cliente(Long id) {
        return clientes.findById(id).orElseThrow(() -> new NoSuchElementException("Usuário não encontrado: " + id));
    }

    private void exigirPromocaoDisponivel() {
        if (!perfis.promocaoDisponivel()) {
            throw new ConflitoException("A promoção de administradores ainda não está ativa no banco: aplique a "
                    + "migração supabase/migrations/20261125000000_administradores.sql.");
        }
    }

    private static Perfil perfilPedido(String perfil) {
        if (perfil == null || perfil.isBlank()) {
            return Perfil.USUARIO;
        }
        try {
            return Perfil.valueOf(perfil.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DadosInvalidosException("Perfil inválido: use USUARIO ou ADMIN.");
        }
    }

    private AdminDtos.Usuario paraUsuario(Cliente c, boolean promovido, boolean temSenha, long totalDenuncias) {
        boolean fixo = perfis.ehAdminFixo(c.getEmail());
        String perfil = (fixo || promovido) ? Perfil.ADMIN.name() : Perfil.USUARIO.name();
        return new AdminDtos.Usuario(c.getId(), c.getNome(), c.getEmail(), c.getTelefone(), perfil,
                fixo, temSenha, totalDenuncias);
    }
}
