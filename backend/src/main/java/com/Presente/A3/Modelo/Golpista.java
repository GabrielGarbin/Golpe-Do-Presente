package com.Presente.A3.Modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Número/perfil já denunciado como parte do Golpe do Presente.
 *
 * <p>É a unidade da <b>base colaborativa</b>: cada denúncia de uma pessoa
 * diferente aumenta {@link #totalDenuncias}. Ao atingir o limite configurado,
 * o {@link #status} vira {@link StatusGolpista#CONFIRMADO} e o número entra
 * na blacklist.</p>
 *
 * <p>O identificador real do golpista é o <b>telefone</b> (único), porque
 * nome e e-mail costumam ser falsos ou desconhecidos.</p>
 */
@Entity
@Table(name = "golpistas")
public class Golpista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Nome usado pelo golpista (pode ser falso). "Desconhecido" se ninguém informou. */
    private String nome;
    private String email;
    /** Apenas dígitos (DDD + número). Chave única da base. */
    @Column(nullable = false, unique = true, length = 11)
    private String telefone;
    /** Quantidade de pessoas distintas que denunciaram este número. */
    private int totalDenuncias;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusGolpista status = StatusGolpista.SUSPEITO;
    private LocalDateTime dataPrimeiraDenuncia;
    private LocalDateTime dataUltimaDenuncia;

    /** Construtor vazio exigido pelo Spring/Jackson. */
    public Golpista() {
    }

    public Golpista(Long id, String nome, String email, String telefone) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public int getTotalDenuncias() {
        return totalDenuncias;
    }

    public void setTotalDenuncias(int totalDenuncias) {
        this.totalDenuncias = totalDenuncias;
    }

    public StatusGolpista getStatus() {
        return status;
    }

    public void setStatus(StatusGolpista status) {
        this.status = status;
    }

    public LocalDateTime getDataPrimeiraDenuncia() {
        return dataPrimeiraDenuncia;
    }

    public void setDataPrimeiraDenuncia(LocalDateTime dataPrimeiraDenuncia) {
        this.dataPrimeiraDenuncia = dataPrimeiraDenuncia;
    }

    public LocalDateTime getDataUltimaDenuncia() {
        return dataUltimaDenuncia;
    }

    public void setDataUltimaDenuncia(LocalDateTime dataUltimaDenuncia) {
        this.dataUltimaDenuncia = dataUltimaDenuncia;
    }
}
