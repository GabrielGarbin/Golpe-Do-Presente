package com.Presente.A3.Seguranca;

/**
 * Resposta de registro/login: o token JWT e um resumo de quem entrou.
 * O front guarda o token e o envia em cada requisição:
 * {@code Authorization: Bearer <token>}.
 */
public class RespostaAuth {

    /** Dados do cliente que o front pode mostrar (nunca inclui senha nem hash). */
    public static class ClienteResumo {
        private final Long id;
        private final String nome;
        private final String email;
        private final String perfil;
        /** true só para o administrador fixo (ADMINS_EMAIL): libera recursos de demonstração no front. */
        private final boolean adminFixo;

        public ClienteResumo(Long id, String nome, String email, String perfil, boolean adminFixo) {
            this.id = id;
            this.nome = nome;
            this.email = email;
            this.perfil = perfil;
            this.adminFixo = adminFixo;
        }

        public boolean isAdminFixo() {
            return adminFixo;
        }

        public Long getId() {
            return id;
        }

        public String getNome() {
            return nome;
        }

        public String getEmail() {
            return email;
        }

        public String getPerfil() {
            return perfil;
        }
    }

    private final String token;
    private final String tipo = "Bearer";
    private final long expiraEmSegundos;
    private final ClienteResumo cliente;

    public RespostaAuth(String token, long expiraEmSegundos, ClienteResumo cliente) {
        this.token = token;
        this.expiraEmSegundos = expiraEmSegundos;
        this.cliente = cliente;
    }

    public String getToken() {
        return token;
    }

    public String getTipo() {
        return tipo;
    }

    public long getExpiraEmSegundos() {
        return expiraEmSegundos;
    }

    public ClienteResumo getCliente() {
        return cliente;
    }
}
