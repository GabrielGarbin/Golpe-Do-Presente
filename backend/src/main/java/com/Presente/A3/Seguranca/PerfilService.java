package com.Presente.A3.Seguranca;

import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Repositorio.ClienteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide quem é administrador.
 *
 * <ul>
 *   <li><b>Admin fixo</b>: e-mail listado em {@code app.auth.admins-email} (variável
 *       {@code ADMINS_EMAIL}). Garante que sempre exista um administrador e não pode
 *       ser rebaixado nem excluído pelo painel.</li>
 *   <li><b>Admin promovido</b>: cliente com uma linha na tabela {@code administradores},
 *       criada por outro administrador no painel.</li>
 * </ul>
 *
 * <p><b>Tolerante a falhas</b>: se a tabela {@code administradores} ainda não foi
 * criada no banco (migração não aplicada), a consulta falha e o serviço considera
 * só os administradores fixos, em vez de derrubar o login de todo mundo.</p>
 */
@Service
public class PerfilService {

    private static final Logger log = LoggerFactory.getLogger(PerfilService.class);

    private final ConsultaAdministradores administradores;
    private final ClienteRepository clientes;
    private final Set<String> adminsFixos;
    private volatile boolean avisouTabelaAusente;

    public PerfilService(ConsultaAdministradores administradores, ClienteRepository clientes,
                         @Value("${app.auth.admins-email:}") String adminsEmail) {
        this.administradores = administradores;
        this.clientes = clientes;
        this.adminsFixos = Arrays.stream(adminsEmail.split(","))
                .map(PerfilService::normalizarEmail)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toSet());
    }

    /** true se o e-mail está em {@code ADMINS_EMAIL}. */
    public boolean ehAdminFixo(String email) {
        return adminsFixos.contains(normalizarEmail(email));
    }

    /** Perfil atual do cliente: fixo ou promovido = ADMIN; os demais, USUARIO. */
    public Perfil perfilDe(Cliente cliente) {
        if (cliente == null) {
            return Perfil.USUARIO;
        }
        if (ehAdminFixo(cliente.getEmail()) || ehPromovido(cliente.getId())) {
            return Perfil.ADMIN;
        }
        return Perfil.USUARIO;
    }

    /**
     * Perfil de um cliente recém-criado (ainda não pode estar na tabela de promovidos).
     * Não consulta o banco: pode ser usado dentro da transação do cadastro sem risco.
     */
    public Perfil perfilDeNovo(Cliente cliente) {
        return cliente != null && ehAdminFixo(cliente.getEmail()) ? Perfil.ADMIN : Perfil.USUARIO;
    }

    /**
     * Confere no banco, a cada requisição de administrador, se a pessoa ainda é ADMIN.
     * Assim, quem foi rebaixado ou excluído perde o acesso na hora, sem esperar o
     * token expirar.
     */
    public boolean ehAdminAgora(Long clienteId) {
        if (clienteId == null) {
            return false;
        }
        Optional<Cliente> cliente = clientes.findById(clienteId);
        return cliente.isPresent() && perfilDe(cliente.get()) == Perfil.ADMIN;
    }

    /** true se o cliente foi promovido pelo painel (linha na tabela {@code administradores}). */
    public boolean ehPromovido(Long clienteId) {
        if (clienteId == null) {
            return false;
        }
        try {
            return administradores.existe(clienteId);
        } catch (RuntimeException e) {
            avisarTabelaAusente(e);
            return false;
        }
    }

    /** Ids de todos os promovidos (vazio se a tabela não estiver disponível). */
    public Set<Long> idsPromovidos() {
        try {
            return administradores.idsPromovidos();
        } catch (RuntimeException e) {
            avisarTabelaAusente(e);
            return Collections.emptySet();
        }
    }

    /** true se a tabela {@code administradores} existe e pode ser lida (migração aplicada). */
    public boolean promocaoDisponivel() {
        try {
            administradores.existe(-1L);
            return true;
        } catch (RuntimeException e) {
            avisarTabelaAusente(e);
            return false;
        }
    }

    private void avisarTabelaAusente(RuntimeException e) {
        if (!avisouTabelaAusente) {
            avisouTabelaAusente = true;
            log.warn("Não foi possível ler a tabela 'administradores' ({}). Aplique a migração "
                    + "supabase/migrations/20261125000000_administradores.sql. Até lá, só os "
                    + "administradores de ADMINS_EMAIL têm acesso de admin.", e.getClass().getSimpleName());
        }
    }

    static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
