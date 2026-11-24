package com.Presente.A3.Modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Hash da senha de um cliente. Tabela {@code public.credenciais}.
 *
 * <p>Fica separada de {@link Cliente} de propósito: assim o hash nunca é
 * serializado junto com os dados do cliente nas respostas da API, e dados de
 * segurança ficam isolados dos dados de cadastro. A chave primária é o próprio
 * id do cliente (relação 1 para 1).</p>
 */
@Entity
@Table(name = "credenciais")
public class CredencialSenha {

    @Id
    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm = LocalDateTime.now();

    protected CredencialSenha() {
        // exigido pelo JPA
    }

    public CredencialSenha(Long clienteId, String senhaHash) {
        this.clienteId = clienteId;
        this.senhaHash = senhaHash;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
