package com.Presente.A3.Dto;

/**
 * Corpo da requisição {@code POST /prevencao/mensagem}.
 *
 * <p>{@code mensagem} é obrigatória. {@code telefoneRemetente} é opcional:
 * se informado, o sistema também consulta a base colaborativa.</p>
 */
public class MensagemRequest {

    private String mensagem;
    private String telefoneRemetente;

    public String getMensagem() {
        return mensagem;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public String getTelefoneRemetente() {
        return telefoneRemetente;
    }

    public void setTelefoneRemetente(String telefoneRemetente) {
        this.telefoneRemetente = telefoneRemetente;
    }
}
