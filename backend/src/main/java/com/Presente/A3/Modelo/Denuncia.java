package com.Presente.A3.Modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Registro individual de uma denúncia feita por um {@link Cliente} contra
 * um {@link Golpista}.
 *
 * <p>Regra: um mesmo cliente só pode denunciar um mesmo número uma vez.
 * Assim, {@code Golpista.totalDenuncias} representa pessoas diferentes e
 * não pode ser inflado por repetição.</p>
 */
@Entity
@Table(name = "denuncias",
        uniqueConstraints = @UniqueConstraint(name = "denuncias_cliente_golpista_unico",
                columnNames = {"cliente_id", "golpista_id"}))
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Fica nulo se o cliente for excluído: a denúncia continua valendo na contagem. */
    @Column(name = "cliente_id")
    private Long clienteId;
    @Column(name = "golpista_id", nullable = false)
    private Long golpistaId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CanalContato canal;
    /** Loja/marca que o golpista fingiu ser (ex.: "Floricultura X"). Opcional. */
    private String estabelecimentoFalsificado;
    /** Relato livre da vítima. Opcional (máx. 500 caracteres). */
    @Column(length = 500)
    private String descricao;
    @Column(nullable = false)
    private LocalDateTime data;

    public Denuncia() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public Long getGolpistaId() {
        return golpistaId;
    }

    public void setGolpistaId(Long golpistaId) {
        this.golpistaId = golpistaId;
    }

    public CanalContato getCanal() {
        return canal;
    }

    public void setCanal(CanalContato canal) {
        this.canal = canal;
    }

    public String getEstabelecimentoFalsificado() {
        return estabelecimentoFalsificado;
    }

    public void setEstabelecimentoFalsificado(String estabelecimentoFalsificado) {
        this.estabelecimentoFalsificado = estabelecimentoFalsificado;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }
}
