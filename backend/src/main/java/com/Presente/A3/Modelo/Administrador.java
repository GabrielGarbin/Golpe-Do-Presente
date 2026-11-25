package com.Presente.A3.Modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Cliente promovido a administrador pelo painel. Tabela {@code public.administradores}.
 *
 * <p>Há duas formas de alguém ser ADMIN:</p>
 * <ul>
 *   <li><b>fixo</b>: o e-mail está na variável {@code ADMINS_EMAIL} do servidor. Serve
 *       para criar o primeiro administrador e não pode ser rebaixado pelo painel;</li>
 *   <li><b>promovido</b>: tem uma linha nesta tabela (criada por outro administrador).</li>
 * </ul>
 */
@Entity
@Table(name = "administradores")
public class Administrador {

    @Id
    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "promovido_em", nullable = false)
    private LocalDateTime promovidoEm = LocalDateTime.now();

    @Column(name = "promovido_por")
    private Long promovidoPor;

    protected Administrador() {
    }

    public Administrador(Long clienteId, Long promovidoPor) {
        this.clienteId = clienteId;
        this.promovidoPor = promovidoPor;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public LocalDateTime getPromovidoEm() {
        return promovidoEm;
    }

    public Long getPromovidoPor() {
        return promovidoPor;
    }
}
