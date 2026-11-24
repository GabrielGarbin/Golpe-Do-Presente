package com.Presente.A3.Repositorio;

import com.Presente.A3.Modelo.CredencialSenha;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso à tabela {@code credenciais} (hash de senha por cliente). */
public interface CredencialSenhaRepository extends JpaRepository<CredencialSenha, Long> {
}
