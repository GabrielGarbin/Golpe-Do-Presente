package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à tabela {@code clientes} (Supabase/PostgreSQL). */
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
