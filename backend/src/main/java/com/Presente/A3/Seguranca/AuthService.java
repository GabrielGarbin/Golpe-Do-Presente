package com.Presente.A3.Seguranca;

import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Modelo.CredencialSenha;
import com.Presente.A3.Repositorio.ClienteRepository;
import com.Presente.A3.Repositorio.CredencialSenhaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Regras de cadastro e login.
 *
 * <ul>
 *   <li><b>Registro</b>: valida os dados, impede e-mail/telefone repetidos,
 *       grava o cliente e o hash da senha e já devolve um token.</li>
 *   <li><b>Login</b>: confere e-mail e senha. A mensagem de erro é a mesma
 *       para "e-mail inexistente" e "senha errada", para não revelar quais
 *       e-mails têm conta. Falhas repetidas bloqueiam temporariamente
 *       ({@link LimitadorLogin}).</li>
 *   <li><b>Perfil</b>: ADMIN para e-mails de
 *       {@code app.auth.admins-email} e para quem foi promovido no painel
 *       ({@link PerfilService}); todos os demais são USUARIO. O perfil
 *       vai dentro do token, então não pode ser alterado pelo cliente.</li>
 * </ul>
 */
@Service
public class AuthService {

    private final ClienteRepository clientes;
    private final CredencialSenhaRepository credenciais;
    private final SenhaService senhas;
    private final JwtService jwt;
    private final LimitadorLogin limitador;
    private final PerfilService perfis;

    public AuthService(ClienteRepository clientes, CredencialSenhaRepository credenciais,
                       SenhaService senhas, JwtService jwt, LimitadorLogin limitador,
                       PerfilService perfis) {
        this.clientes = clientes;
        this.credenciais = credenciais;
        this.senhas = senhas;
        this.jwt = jwt;
        this.limitador = limitador;
        this.perfis = perfis;
    }

    /**
     * Cria uma conta e já faz o login.
     *
     * @throws DadosInvalidosException dados inválidos (nome, e-mail, telefone ou senha fraca)
     * @throws ConflitoException       e-mail ou telefone já cadastrado
     */
    @Transactional
    public RespostaAuth registrar(RegistroRequest req) {
        if (req == null) {
            throw new DadosInvalidosException("Corpo da requisição é obrigatório.");
        }
        String nome = ValidacaoCadastro.nome(req.getNome());
        String email = ValidacaoCadastro.email(req.getEmail());
        String telefone = ValidacaoCadastro.telefone(req.getTelefone());
        String senha = ValidacaoCadastro.senha(req.getSenha());

        if (clientes.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe uma conta com este e-mail.");
        }
        if (clientes.existsByTelefone(telefone)) {
            throw new ConflitoException("Já existe uma conta com este telefone.");
        }

        Cliente cliente = clientes.save(new Cliente(null, nome, email, telefone));
        credenciais.save(new CredencialSenha(cliente.getId(), senhas.gerarHash(senha)));
        // Conta nova não pode estar entre os promovidos: não consulta a tabela dentro desta transação.
        return resposta(cliente, perfis.perfilDeNovo(cliente));
    }

    /**
     * Confere e-mail e senha e devolve um token.
     *
     * @param ip endereço de quem chama, usado só para limitar tentativas
     * @throws AutenticacaoException    e-mail ou senha inválidos
     * @throws MuitasTentativasException excesso de falhas recentes
     */
    public RespostaAuth login(LoginRequest req, String ip) {
        if (req == null || req.getEmail() == null || req.getEmail().isBlank()
                || req.getSenha() == null || req.getSenha().isEmpty()) {
            throw new DadosInvalidosException("Informe e-mail e senha.");
        }
        String email = normalizarEmail(req.getEmail());
        String chave = ip + "|" + email;
        if (limitador.bloqueado(chave)) {
            throw new MuitasTentativasException(
                    "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
        }

        Optional<Cliente> cliente = clientes.findFirstByEmailIgnoreCaseOrderByIdAsc(email);
        Optional<CredencialSenha> credencial = cliente.flatMap(c -> credenciais.findById(c.getId()));

        boolean senhaOk;
        if (credencial.isPresent()) {
            senhaOk = senhas.confere(req.getSenha(), credencial.get().getSenhaHash());
        } else {
            senhas.gastarTempo(req.getSenha()); // mesmo tempo de resposta, exista ou não o e-mail
            senhaOk = false;
        }

        if (!senhaOk) {
            limitador.registrarFalha(chave);
            throw new AutenticacaoException("E-mail ou senha inválidos.");
        }
        limitador.limpar(chave);
        return resposta(cliente.get(), perfis.perfilDe(cliente.get()));
    }

    /** Dados do cliente dono do token (usado por {@code GET /auth/eu}). */
    public RespostaAuth.ClienteResumo eu(UsuarioAutenticado usuario) {
        Cliente c = clientes.findById(usuario.clienteId())
                .orElseThrow(() -> new AutenticacaoException("A conta deste token não existe mais."));
        // Perfil ATUAL (e não o do token): reflete na hora uma promoção ou um rebaixamento.
        return resumo(c, perfis.perfilDe(c));
    }

    private RespostaAuth resposta(Cliente c, Perfil perfil) {
        return new RespostaAuth(jwt.gerar(c.getId(), perfil), jwt.getExpiracaoSegundos(), resumo(c, perfil));
    }

    private RespostaAuth.ClienteResumo resumo(Cliente c, Perfil perfil) {
        return new RespostaAuth.ClienteResumo(c.getId(), c.getNome(), c.getEmail(), perfil.name(),
                perfis.ehAdminFixo(c.getEmail()));
    }

    private static String normalizarEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
