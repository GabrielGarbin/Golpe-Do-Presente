/** Consultar número na base colaborativa. */
import { Api, ultimaVence, foiCancelada } from '../api.js';
import { $, revelar, digits, validTel, fdate, setField, invalido } from '../util.js';
import { resultado, vazio, carregando, erro, TEXTO_TELEFONE } from '../ui/resultado.js';
import { ir } from '../router.js';

const novoSinal = ultimaVence();
const box = () => $('#rConsultar');
let ultimoConsultado = null;

async function consultar(numero) {
  if (!validTel(numero)) {
    box().innerHTML = vazio('phone', 'Número inválido', 'Informe DDD + número (10 ou 11 dígitos).');
    return invalido('#cNum');
  }
  ultimoConsultado = digits(numero);
  history.replaceState(null, '', `#/consultar?numero=${ultimoConsultado}`);
  box().innerHTML = carregando('Consultando a base colaborativa…');
  const signal = novoSinal();

  try {
    // Paralelo: risco do número + histórico do golpista (se existir).
    const [r, golpistas] = await Promise.all([
      Api.telefone(numero, { signal }),
      Api.blacklist().catch(() => [])
    ]);
    const g = golpistas.find(x => x.telefone === digits(numero));
    const stats = g ? `<div class="r-stats">
        <div><b>${g.totalDenuncias}</b><span>denúncias</span></div>
        <div><b>${fdate(g.dataPrimeiraDenuncia)}</b><span>primeira denúncia</span></div>
        <div><b>${fdate(g.dataUltimaDenuncia)}</b><span>última denúncia</span></div>
        <div><b>${g.status === 'CONFIRMADO' ? 'Confirmado' : 'Suspeito'}</b><span>situação</span></div>
      </div>` : '';

    box().innerHTML = resultado(r, TEXTO_TELEFONE, {
      antes: stats,
      acoes: `<button class="btn logado-only" data-a="den">Denunciar este número</button>
              <button class="btn-ghost" data-a="ori">Ver orientações</button>`
    });
    box().querySelector('[data-a="den"]').onclick = () => ir('denunciar', { tel: numero });
    box().querySelector('[data-a="ori"]').onclick = () => ir('orientacoes');
    revelar(box());
  } catch (e) {
    if (!foiCancelada(e)) box().innerHTML = erro(e.message);
  }
}

export function init() {
  box().innerHTML = vazio('phone', 'Nenhuma consulta ainda', 'Digite o número que entrou em contato com você para ver se ele já foi denunciado.');
  $('#fConsultar').addEventListener('submit', e => { e.preventDefault(); consultar($('#cNum').value); });
}

export function show({ numero }) {
  if (numero && digits(numero) !== ultimoConsultado) {
    setField('#cNum', numero);
    consultar(numero);
  }
  if (!numero) $('#cNum').focus();
}
