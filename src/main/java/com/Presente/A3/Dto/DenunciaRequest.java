package com.Presente.A3.Dto;

import com.Presente.A3.Modelo.CanalContato;

/**
 * Corpo da requisição {@code POST /denuncias}.
 *
 * <p>Campos obrigatórios: {@code clienteId} e {@code telefoneGolpista}.
 * Os demais ajudam nas estatísticas, mas são opcionais.</p>
 */
public class DenunciaRequest {

    private Long clienteId;
    private String telefoneGolpista;
    private String nomeGolpista;
    private CanalContato canal;
    private String estabelecimentoFalsificado;
    private String descricao;

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getTelefoneGolpista() {
        return telefoneGolpista;
    }

    public void setTelefoneGolpista(String telefoneGolpista) {
        this.telefoneGolpista = telefoneGolpista;
    }

    public String getNomeGolpista() {
        return nomeGolpista;
    }

    public void setNomeGolpista(String nomeGolpista) {
        this.nomeGolpista = nomeGolpista;
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
}
