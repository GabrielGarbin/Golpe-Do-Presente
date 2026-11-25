package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.Administrador;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à tabela {@code administradores} (clientes promovidos a ADMIN pelo painel). */
public interface AdministradorRepository extends JpaRepository<Administrador, Long> {
}
