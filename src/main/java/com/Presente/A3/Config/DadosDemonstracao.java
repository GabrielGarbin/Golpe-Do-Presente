package com.Presente.A3.Config;

import com.Presente.A3.Dto.DenunciaRequest;
import com.Presente.A3.Modelo.CanalContato;
import com.Presente.A3.Modelo.Cliente;
import com.Presente.A3.Service.ClienteService;
import com.Presente.A3.Service.DenunciaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carrega dados fictícios ao iniciar a aplicação, para que a solução possa
 * ser demonstrada imediatamente, sem cadastrar nada à mão.
 *
 * <p>O que fica pronto:</p>
 * <ul>
 *   <li>5 clientes (ids 1 a 5);</li>
 *   <li>um número CONFIRMADO na blacklist (11 90000-0001, 3 denúncias);</li>
 *   <li>um número SUSPEITO com 1 denúncia (21 90000-0002), que a demonstração
 *       pode levar à blacklist denunciando com mais duas pessoas.</li>
 * </ul>
 *
 * <p>Para desligar: {@code app.demo.seed=false} no {@code application.properties}.
 * Todos os nomes e números são fictícios.</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true", matchIfMissing = true)
public class DadosDemonstracao implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);

    private final ClienteService clienteService;
    private final DenunciaService denunciaService;

    public DadosDemonstracao(ClienteService clienteService, DenunciaService denunciaService) {
        this.clienteService = clienteService;
        this.denunciaService = denunciaService;
    }

    @Override
    public void run(String... args) {
        // ids gerados em sequência: 1=Ana, 2=Bruno, 3=Carla, 4=Diego, 5=Elisa
        clienteService.save(new Cliente(null, "Ana Souza", "ana@example.com", "(11) 91111-0001"));
        clienteService.save(new Cliente(null, "Bruno Lima", "bruno@example.com", "(11) 91111-0002"));
        clienteService.save(new Cliente(null, "Carla Mendes", "carla@example.com", "(11) 91111-0003"));
        clienteService.save(new Cliente(null, "Diego Alves", "diego@example.com", "(21) 91111-0004"));
        clienteService.save(new Cliente(null, "Elisa Rocha", "elisa@example.com", "(21) 91111-0005"));

        // Três pessoas diferentes denunciam o mesmo número => vira CONFIRMADO.
        for (long cliente = 1; cliente <= 3; cliente++) {
            denunciar(cliente, "(11) 90000-0001", "Floricultura Bela Flor",
                    CanalContato.WHATSAPP, "Disse que ganhei um brinde e cobrou taxa na maquininha.");
        }

        // Apenas uma denúncia => continua SUSPEITO.
        denunciar(4L, "(21) 90000-0002", "Perfumaria Aroma",
                CanalContato.SMS, "Mensagem de brinde com link para confirmar endereço.");

        log.info("Dados de demonstração carregados: 5 clientes, 4 denúncias, 1 número na blacklist.");
    }

    private void denunciar(Long clienteId, String telefone, String loja,
                           CanalContato canal, String descricao) {
        DenunciaRequest req = new DenunciaRequest();
        req.setClienteId(clienteId);
        req.setTelefoneGolpista(telefone);
        req.setEstabelecimentoFalsificado(loja);
        req.setCanal(canal);
        req.setDescricao(descricao);
        denunciaService.registrar(req);
    }
}
