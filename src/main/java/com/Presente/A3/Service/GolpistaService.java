package com.Presente.A3.Service;

import com.Presente.A3.Modelo.Golpista;
import com.Presente.A3.Modelo.StatusGolpista;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

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
 * <p>Persistência em memória, como em {@link ClienteService}.</p>
 */
@Service
public class GolpistaService {

    private static final String NOME_PADRAO = "Desconhecido";

    private final ConcurrentSkipListMap<Long, Golpista> golpistas = new ConcurrentSkipListMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    /** Nº de pessoas distintas que precisam denunciar para confirmar o golpista. */
    private final int limiteConfirmacao;

    /**
     * @param limiteConfirmacao configurável em {@code application.properties}
     *                          via {@code app.denuncias.limite-confirmacao}
     *                          (padrão: 3)
     */
    public GolpistaService(@Value("${app.denuncias.limite-confirmacao:3}") int limiteConfirmacao) {
        if (limiteConfirmacao < 1) {
            throw new IllegalArgumentException("O limite de confirmação deve ser pelo menos 1.");
        }
        this.limiteConfirmacao = limiteConfirmacao;
    }

    public int getLimiteConfirmacao() {
        return limiteConfirmacao;
    }

    /** @return todos os golpistas (suspeitos e confirmados), em ordem de id */
    public List<Golpista> findAll() {
        return new ArrayList<>(golpistas.values());
    }

    public Optional<Golpista> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(golpistas.get(id));
    }

    /**
     * Procura um golpista pelo telefone, aceitando qualquer formatação.
     *
     * @throws IllegalArgumentException se o telefone for inválido
     */
    public Optional<Golpista> findByTelefone(String telefone) {
        String normalizado = TelefoneUtil.normalizar(telefone);
        return golpistas.values().stream()
                .filter(g -> normalizado.equals(g.getTelefone()))
                .findFirst();
    }

    /**
     * Cadastro manual (uso administrativo). Contadores e status são sempre
     * zerados: quem envia o JSON não pode "fabricar" uma blacklist.
     *
     * @throws IllegalStateException se o telefone já estiver cadastrado
     */
    public synchronized Golpista save(Golpista golpista) {
        String telefone = TelefoneUtil.normalizar(golpista.getTelefone());
        if (findByTelefone(telefone).isPresent()) {
            throw new IllegalStateException("Este telefone já consta na base de golpistas.");
        }
        return criar(telefone, golpista.getNome(), golpista.getEmail());
    }

    /** Atualiza apenas nome, e-mail e telefone (contagem e status são automáticos). */
    public synchronized Optional<Golpista> update(Long id, Golpista atualizado) {
        Optional<Golpista> existente = findById(id);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        String telefone = TelefoneUtil.normalizar(atualizado.getTelefone());
        Optional<Golpista> comMesmoTelefone = findByTelefone(telefone);
        if (comMesmoTelefone.isPresent() && !comMesmoTelefone.get().getId().equals(id)) {
            throw new IllegalStateException("Este telefone já consta na base de golpistas.");
        }
        Golpista g = existente.get();
        g.setNome(nomeOuPadrao(atualizado.getNome()));
        g.setEmail(atualizado.getEmail());
        g.setTelefone(telefone);
        return Optional.of(g);
    }

    public boolean deleteById(Long id) {
        return id != null && golpistas.remove(id) != null;
    }

    /**
     * Devolve o golpista do telefone informado; se ninguém o denunciou antes,
     * cria um registro novo como {@code SUSPEITO}. É o ponto de entrada da
     * primeira denúncia contra um número.
     *
     * @param telefoneNormalizado telefone já passado por {@link TelefoneUtil}
     */
    public synchronized Golpista obterOuCriarSuspeito(String telefoneNormalizado, String nome) {
        return findByTelefone(telefoneNormalizado)
                .orElseGet(() -> criar(telefoneNormalizado, nome, null));
    }

    /**
     * Contabiliza mais uma denúncia (de uma pessoa distinta) e promove o
     * golpista a {@code CONFIRMADO} ao atingir o limite.
     */
    public synchronized Golpista registrarDenuncia(Golpista golpista) {
        LocalDateTime agora = LocalDateTime.now();
        golpista.setTotalDenuncias(golpista.getTotalDenuncias() + 1);
        if (golpista.getDataPrimeiraDenuncia() == null) {
            golpista.setDataPrimeiraDenuncia(agora);
        }
        golpista.setDataUltimaDenuncia(agora);

        if (golpista.getTotalDenuncias() >= limiteConfirmacao) {
            golpista.setStatus(StatusGolpista.CONFIRMADO);
        }
        return golpista;
    }

    /**
     * Blacklist: apenas golpistas CONFIRMADOS, dos mais denunciados para os
     * menos denunciados.
     */
    public List<Golpista> listarBlacklist() {
        List<Golpista> lista = new ArrayList<>();
        for (Golpista g : golpistas.values()) {
            if (g.getStatus() == StatusGolpista.CONFIRMADO) {
                lista.add(g);
            }
        }
        lista.sort(Comparator.comparingInt(Golpista::getTotalDenuncias).reversed());
        return lista;
    }

    private Golpista criar(String telefone, String nome, String email) {
        Golpista g = new Golpista();
        g.setId(sequencia.incrementAndGet());
        g.setTelefone(telefone);
        g.setNome(nomeOuPadrao(nome));
        g.setEmail(email);
        g.setStatus(StatusGolpista.SUSPEITO);
        g.setTotalDenuncias(0);
        golpistas.put(g.getId(), g);
        return g;
    }

    private String nomeOuPadrao(String nome) {
        return (nome == null || nome.isBlank()) ? NOME_PADRAO : nome.trim();
    }
}
