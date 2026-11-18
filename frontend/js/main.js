/** Ponto de entrada do front-end. */
import { Api } from './api.js';
import { $, bindMasks } from './util.js';
import { paintIcons } from './icons.js';
import { initSession } from './session.js';
import { initAcessibilidade } from './acessibilidade.js';
import { registrar, iniciarRotas } from './router.js';

import * as painel from './views/painel.js';
import * as consultar from './views/consultar.js';
import * as mensagem from './views/mensagem.js';
import * as cnpj from './views/cnpj.js';
import * as assistente from './views/assistente.js';
import * as denunciar from './views/denunciar.js';
import * as orientacoes from './views/orientacoes.js';

/* Indicador de conexão com a API (verifica a cada 30 s, em segundo plano). */
async function verificarApi() {
  const el = $('#apiStatus');
  try {
    await Api.ping();
    el.dataset.state = 'online';
    el.querySelector('span').textContent = 'API conectada';
    el.title = 'API em ' + Api.url;
  } catch {
    el.dataset.state = 'offline';
    el.querySelector('span').textContent = 'API indisponível';
    el.title = 'Não foi possível conectar em ' + Api.url;
  }
}

paintIcons();
bindMasks();
initSession();
initAcessibilidade();

registrar('painel', painel);
registrar('consultar', consultar);
registrar('mensagem', mensagem);
registrar('cnpj', cnpj);
registrar('assistente', assistente);
registrar('denunciar', denunciar);
registrar('orientacoes', orientacoes);

paintIcons(); // ícones criados pelas telas no init()
iniciarRotas();

verificarApi();
setInterval(verificarApi, 30000);

