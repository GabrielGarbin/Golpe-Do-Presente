package com.Presente.A3.Seguranca;

import com.Presente.A3.Modelo.Administrador;
import com.Presente.A3.Repositorio.AdministradorRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leituras da tabela {@code administradores}, cada uma em uma transação própria
 * ({@code REQUIRES_NEW}).
 *
 * <p>Motivo: no PostgreSQL, uma consulta que falha (por exemplo, tabela ainda não
 * criada) invalida a transação inteira. Isolando a leitura, uma falha aqui nunca
 * derruba a operação de quem chamou (cadastro, exclusão etc.); o
 * {@link PerfilService} trata o erro e segue só com os administradores fixos.</p>
 */
@Component
public class ConsultaAdministradores {

    private final AdministradorRepository repositorio;

    public ConsultaAdministradores(AdministradorRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean existe(Long clienteId) {
        return repositorio.existsById(clienteId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Set<Long> idsPromovidos() {
        return repositorio.findAll().stream().map(Administrador::getClienteId).collect(Collectors.toSet());
    }
}
