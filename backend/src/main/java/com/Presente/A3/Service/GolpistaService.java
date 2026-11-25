package com.Presente.A3.Service;

import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import com.Presente.A3.Repositorio.GolpistaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Gerencia a <b>base colaborativa</b> de números/perfis golpistas.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>CRUD administrativo de golpistas;</li>
 *   <li>consulta por telefone (usada pela prevenção);</li>
 *   <li>contagem de denúncias e promoção automática de
 *       {@code SUSPEITO} para {@code CONFIRMADO};</li>
 *   <li>emissão da blacklist.</li>
 * </ul>
 *
 * <p>Persistência na tabela {@code golpistas} do Supabase (PostgreSQL). O
 * telefone tem restrição UNIQUE no banco, então dois cadastros simultâneos do
 * mesmo número não geram duplicata.</p>
 */
@Service
public class GolpistaService {

    private static final String NOME_PADRAO = "Desconhecido";

    private final GolpistaRepository repositorio;

    /** Nº de pessoas distintas que precisam denunciar para confirmar o golpista. */
    private final int limiteConfirmacao;

    /**
     * @param limiteConfirmacao configurável em {@code application.properties}
     *                          via {@code app.denuncias.limite-confirmacao}
     *                          (padrão: 3)
     */
    public GolpistaService(GolpistaRepository repositorio,
                           @Value("${app.denuncias.limite-confirmacao:3}") int limiteConfirmacao) {
        if (limiteConfirmacao < 1) {
            throw new IllegalArgumentException("O limite de confirmação deve ser pelo menos 1.");
        }
        this.repositorio = repositorio;
        this.limiteConfirmacao = limiteConfirmacao;
    }

    public int getLimiteConfirmacao() {
        return limiteConfirmacao;
    }

    /** @return todos os golpistas (suspeitos e confirmados), em ordem de id */
    public List<Golpista> findAll() {
        return repositorio.findAll(Sort.by("id"));
    }

    public long count() {
        return repositorio.count();
    }

    public Optional<Golpista> findById(Long id) {
        return id == null ? Optional.empty() : repositorio.findById(id);
    }

    /**
     * Procura um golpista pelo telefone, aceitando qualquer formatação.
     *
     * @throws IllegalArgumentException se o telefone for inválido
     */
    public Optional<Golpista> findByTelefone(String telefone) {
        return repositorio.findByTelefone(TelefoneUtil.normalizar(telefone));
    }

    /**
     * Cadastro manual (uso administrativo). Contadores e status são sempre
     * zerados: quem envia o JSON não pode "fabricar" uma blacklist.
     *
     * @throws IllegalStateException se o telefone já estiver cadastrado
     */
    @Transactional
    public Golpista save(Golpista golpista) {
        String telefone = TelefoneUtil.normalizar(golpista.getTelefone());
        if (repositorio.findByTelefone(telefone).isPresent()) {
            throw new IllegalStateException("Este telefone já consta na base de golpistas.");
        }
        return criar(telefone, golpista.getNome(), golpista.getEmail());
    }

    /** Atualiza apenas nome, e-mail e telefone (contagem e status são automáticos). */
    @Transactional
    public Optional<Golpista> update(Long id, Golpista atualizado) {
        Optional<Golpista> existente = findById(id);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        String telefone = TelefoneUtil.normalizar(atualizado.getTelefone());
        Optional<Golpista> comMesmoTelefone = repositorio.findByTelefone(telefone);
        if (comMesmoTelefone.isPresent() && !comMesmoTelefone.get().getId().equals(id)) {
            throw new IllegalStateException("Este telefone já consta na base de golpistas.");
        }
        Golpista g = existente.get();
        g.setNome(nomeOuPadrao(atualizado.getNome()));
        g.setEmail(atualizado.getEmail());
        g.setTelefone(telefone);
        return Optional.of(repositorio.save(g));
    }

    /** Exclui o golpista; as denúncias contra ele são apagadas em cascata pelo banco. */
    @Transactional
    public boolean deleteById(Long id) {
        if (id == null || !repositorio.existsById(id)) {
            return false;
        }
        repositorio.deleteById(id);
        return true;
    }

    /**
     * Devolve o golpista do telefone informado; se ninguém o denunciou antes,
     * cria um registro novo como {@code SUSPEITO}. É o ponto de entrada da
     * primeira denúncia contra um número.
     *
     * @param telefoneNormalizado telefone já passado por {@link TelefoneUtil}
     */
    @Transactional
    public Golpista obterOuCriarSuspeito(String telefoneNormalizado, String nome) {
        return repositorio.findByTelefone(telefoneNormalizado)
                .orElseGet(() -> criar(telefoneNormalizado, nome, null));
    }

    /**
     * Contabiliza mais uma denúncia (de uma pessoa distinta) e promove o
     * golpista a {@code CONFIRMADO} ao atingir o limite.
     *
     * <p>A soma é feita pelo próprio banco (UPDATE atômico), então denúncias
     * simultâneas contra o mesmo número não se perdem.</p>
     *
     * @return o golpista com os valores atualizados
     */
    @Transactional
    public Golpista registrarDenuncia(Golpista golpista) {
        int alteradas = repositorio.somarDenuncia(golpista.getId(), LocalDateTime.now(),
                limiteConfirmacao, StatusGolpista.CONFIRMADO);
        if (alteradas == 0) {
            throw new NoSuchElementException("Golpista não encontrado: " + golpista.getId());
        }
        return repositorio.findById(golpista.getId()).orElseThrow();
    }

    /**
     * Blacklist: apenas golpistas CONFIRMADOS, dos mais denunciados para os
     * menos denunciados.
     */
    public List<Golpista> listarBlacklist() {
        return repositorio.findByStatusOrderByTotalDenunciasDescIdAsc(StatusGolpista.CONFIRMADO);
    }

    /**
     * Recalcula contador, datas e status a partir das denúncias que sobraram
     * (usado quando o administrador remove denúncias). Sem denúncias restantes,
     * o número sai da base.
     *
     * @param restantes denúncias que continuam contra este número
     * @return o golpista atualizado, ou vazio se ele foi removido
     */
    @Transactional
    public Optional<Golpista> recalcular(Long golpistaId, List<Denuncia> restantes) {
        Optional<Golpista> existente = findById(golpistaId);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        if (restantes.isEmpty()) {
            repositorio.deleteById(golpistaId);
            return Optional.empty();
        }
        Golpista g = existente.get();
        g.setTotalDenuncias(restantes.size());
        g.setStatus(restantes.size() >= limiteConfirmacao ? StatusGolpista.CONFIRMADO : StatusGolpista.SUSPEITO);
        g.setDataPrimeiraDenuncia(restantes.stream().map(Denuncia::getData)
                .min(Comparator.naturalOrder()).orElse(null));
        g.setDataUltimaDenuncia(restantes.stream().map(Denuncia::getData)
                .max(Comparator.naturalOrder()).orElse(null));
        return Optional.of(repositorio.save(g));
    }

    /**
     * Moderação: o administrador confirma um número manualmente (entra na blacklist)
     * ou o devolve a SUSPEITO. Novas denúncias continuam seguindo a regra automática.
     *
     * @throws NoSuchElementException se o número não existe
     */
    @Transactional
    public Golpista alterarStatus(Long id, StatusGolpista status) {
        if (status == null) {
            throw new IllegalArgumentException("Informe o status: SUSPEITO ou CONFIRMADO.");
        }
        Golpista g = findById(id).orElseThrow(() -> new NoSuchElementException("Número não encontrado: " + id));
        g.setStatus(status);
        return repositorio.save(g);
    }

    public long countConfirmados() {
        return repositorio.countByStatus(StatusGolpista.CONFIRMADO);
    }

    private Golpista criar(String telefone, String nome, String email) {
        Golpista g = new Golpista();
        g.setTelefone(telefone);
        g.setNome(nomeOuPadrao(nome));
        g.setEmail(email);
        g.setStatus(StatusGolpista.SUSPEITO);
        g.setTotalDenuncias(0);
        return repositorio.save(g);
    }

    private String nomeOuPadrao(String nome) {
        return (nome == null || nome.isBlank()) ? NOME_PADRAO : nome.trim();
    }
}
