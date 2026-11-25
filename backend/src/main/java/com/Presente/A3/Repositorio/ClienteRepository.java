package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Acesso à tabela {@code clientes} (Supabase/PostgreSQL). */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /** Busca para login, ignorando maiúsculas/minúsculas no e-mail. */
    Optional<Cliente> findFirstByEmailIgnoreCaseOrderByIdAsc(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTelefone(String telefone);

    /** Edição pelo admin: o e-mail pode repetir o do próprio cliente, mas não o de outro. */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByTelefoneAndIdNot(String telefone, Long id);
}
