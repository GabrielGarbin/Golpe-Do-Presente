package com.Presente.A3.Admin;

import java.time.LocalDateTime;

/** Formatos de entrada e saída da área de administração ({@code /admin}). */
public final class AdminDtos {

    private AdminDtos() {
    }

    /** Usuário visto pelo administrador. Nunca inclui senha nem hash. */
    public record Usuario(Long id, String nome, String email, String telefone, String perfil,
                          boolean adminFixo, boolean temSenha, long totalDenuncias) {
    }

    /** Cadastro de usuário pelo administrador. {@code perfil}: USUARIO (padrão) ou ADMIN. */
    public record NovoUsuario(String nome, String email, String telefone, String senha, String perfil) {
    }

    /** Edição dos dados cadastrais (senha e perfil têm rotas próprias). */
    public record EdicaoUsuario(String nome, String email, String telefone) {
    }

    public record NovaSenha(String senha) {
    }

    public record NovoPerfil(String perfil) {
    }

    public record NovoStatus(String status) {
    }

    /** Denúncia com os dados de quem denunciou e do número denunciado, para moderação. */
    public record Denuncia(Long id, LocalDateTime data, String canal, String estabelecimentoFalsificado,
                           String descricao, Long clienteId, String clienteNome, String clienteEmail,
                           Long golpistaId, String golpistaTelefone, String golpistaStatus,
                           Integer golpistaTotalDenuncias) {
    }

    /** Resultado da remoção de uma denúncia: como ficou o número denunciado. */
    public record RemocaoDenuncia(Long denunciaId, Long golpistaId, boolean numeroRemovido,
                                  Integer totalDenunciasRestantes, String statusAtual) {
    }

    /** Indicadores do topo da área de administração. */
    public record Resumo(long usuarios, long administradores, long denuncias, long denunciasUltimos7Dias,
                         long numeros, long numerosConfirmados, int limiteConfirmacao) {
    }
}
