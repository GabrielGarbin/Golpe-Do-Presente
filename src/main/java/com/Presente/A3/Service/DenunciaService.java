package com.Presente.A3.Service;

import com.Presente.A3.Dto.DenunciaRequest;
import com.Presente.A3.Modelo.CanalContato;
import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Modelo.Denuncia;
import com.Presente.A3.Modelo.Golpista;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Núcleo da <b>denúncia colaborativa</b>.
 *
 * <p>Fluxo de {@link #registrar(DenunciaRequest)}:</p>
 * <ol>
 *   <li>valida o denunciante e o telefone informado;</li>
 *   <li>impede denunciar o próprio número e denunciar o mesmo número duas vezes;</li>
 *   <li>cria o golpista como SUSPEITO se for a primeira denúncia contra ele;</li>
 *   <li>grava a denúncia e atualiza o contador (podendo confirmar o golpista).</li>
 * </ol>
 *
 * <p>As regras 2 e 3 evitam abuso: sem elas, uma única pessoa poderia
 * "queimar" o número de qualquer um repetindo a denúncia.</p>
 */
@Service
public class DenunciaService {

    private static final int TAMANHO_MAX_DESCRICAO = 500;

    private final ClienteService clienteService;
    private final GolpistaService golpistaService;

    private final ConcurrentSkipListMap<Long, Denuncia> denuncias = new ConcurrentSkipListMap<>();
    private final AtomicLong sequencia = new AtomicLong();

    public DenunciaService(ClienteService clienteService, GolpistaService golpistaService) {
        this.clienteService = clienteService;
        this.golpistaService = golpistaService;
    }

    /**
     * Registra uma denúncia e atualiza a base colaborativa.
     *
     * @throws IllegalArgumentException dados inválidos ou tentativa de denunciar o próprio número
     * @throws NoSuchElementException   cliente denunciante não existe
     * @throws IllegalStateException    este cliente já denunciou este número
     */
    public synchronized Denuncia registrar(DenunciaRequest req) {
        if (req == null || req.getClienteId() == null) {
            throw new IllegalArgumentException("clienteId é obrigatório.");
        }
        if (req.getDescricao() != null && req.getDescricao().length() > TAMANHO_MAX_DESCRICAO) {
            throw new IllegalArgumentException(
                    "A descrição deve ter no máximo " + TAMANHO_MAX_DESCRICAO + " caracteres.");
        }

        Cliente cliente = clienteService.findById(req.getClienteId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Cliente não encontrado: " + req.getClienteId()));

        String telefone = TelefoneUtil.normalizar(req.getTelefoneGolpista());
        if (telefone.equals(cliente.getTelefone())) {
            throw new IllegalArgumentException("Você não pode denunciar o seu próprio número.");
        }

        // Se o número já é conhecido, confere se este cliente já o denunciou.
        Optional<Golpista> conhecido = golpistaService.findByTelefone(telefone);
        if (conhecido.isPresent() && jaDenunciou(cliente.getId(), conhecido.get().getId())) {
            throw new IllegalStateException("Você já denunciou este número.");
        }

        Golpista golpista = conhecido.orElseGet(
                () -> golpistaService.obterOuCriarSuspeito(telefone, req.getNomeGolpista()));

        Denuncia d = new Denuncia();
        d.setId(sequencia.incrementAndGet());
        d.setClienteId(cliente.getId());
        d.setGolpistaId(golpista.getId());
        d.setCanal(req.getCanal() != null ? req.getCanal() : CanalContato.OUTRO);
        d.setEstabelecimentoFalsificado(req.getEstabelecimentoFalsificado());
        d.setDescricao(req.getDescricao());
        d.setData(LocalDateTime.now());
        denuncias.put(d.getId(), d);

        golpistaService.registrarDenuncia(golpista); // pode promover a CONFIRMADO
        return d;
    }

    public List<Denuncia> listar() {
        return new ArrayList<>(denuncias.values());
    }

    public Optional<Denuncia> buscarPorId(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(denuncias.get(id));
    }

    /**
     * Indicadores agregados da base. Servem de insumo para a seção
     * "Discussão dos resultados" do artigo e para um futuro dashboard.
     */
    public Map<String, Object> estatisticas() {
        List<Denuncia> todas = listar();

        Map<String, Long> porCanal = todas.stream().collect(Collectors.groupingBy(
                d -> d.getCanal().name(), TreeMap::new, Collectors.counting()));

        // Ranking das 5 marcas mais imitadas (ignora vazios e diferenças de maiúsculas).
        Map<String, Long> topEstabelecimentos = todas.stream()
                .map(Denuncia::getEstabelecimentoFalsificado)
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase())
                .collect(Collectors.groupingBy(e -> e, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("totalDenuncias", todas.size());
        resultado.put("numerosDenunciados", golpistaService.findAll().size());
        resultado.put("numerosNaBlacklist", golpistaService.listarBlacklist().size());
        resultado.put("limiteConfirmacao", golpistaService.getLimiteConfirmacao());
        resultado.put("denunciasPorCanal", porCanal);
        resultado.put("estabelecimentosMaisImitados", topEstabelecimentos);
        return resultado;
    }

    private boolean jaDenunciou(Long clienteId, Long golpistaId) {
        return denuncias.values().stream().anyMatch(
                d -> d.getClienteId().equals(clienteId) && d.getGolpistaId().equals(golpistaId));
    }
}
