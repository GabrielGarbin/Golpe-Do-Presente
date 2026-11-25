#!/usr/bin/env bash
# Inicia o sistema inteiro (API + front-end) e abre o navegador.
# Uso: ./iniciar.sh     Para parar: Ctrl+C (encerra os dois).
set -euo pipefail
cd "$(dirname "$0")"

URL_API="http://localhost:8081/saude"
URL_FRONT="http://localhost:5500"
# Comandos (podem ser trocados por variáveis de ambiente, útil para testes)
CMD_API="${CMD_API:-./mvnw spring-boot:run}"
if [ -z "${CMD_FRONT:-}" ]; then
  if command -v python3 >/dev/null 2>&1; then CMD_FRONT="python3 -m http.server 5500"
  elif command -v npx >/dev/null 2>&1; then CMD_FRONT="npx --yes serve -l 5500 ."
  else echo "Não encontrei Python 3 nem Node.js para servir o front-end." >&2; exit 1; fi
fi

if [ ! -f backend/config/application.properties ] && [ ! -f backend/.env ]; then
  echo "Falta a configuração do banco: crie backend/config/application.properties (veja o README)." >&2
  exit 1
fi

porta_em_uso() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

pids=()
encerrar() { for p in "${pids[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done; }
trap encerrar EXIT
trap 'exit 130' INT TERM

echo "Golpe do Presente: iniciando API e front-end"
if porta_em_uso 8081; then echo "API: já está rodando na porta 8081."
else
  echo "API: iniciando (a primeira vez demora mais: o Maven baixa as dependências)..."
  (cd backend && exec $CMD_API) > api.log 2>&1 &
  pids+=("$!")
fi
if porta_em_uso 5500; then echo "Front-end: já está rodando na porta 5500."
else
  (cd frontend && exec $CMD_FRONT) > front.log 2>&1 &
  pids+=("$!")
fi

printf "Aguardando a API ficar pronta"
for _ in $(seq 1 80); do
  if curl -fsS --max-time 3 "$URL_API" >/dev/null 2>&1; then
    echo; echo "Tudo pronto: $URL_FRONT   (logs: api.log e front.log; Ctrl+C para parar)"
    (command -v xdg-open >/dev/null && xdg-open "$URL_FRONT" || command -v open >/dev/null && open "$URL_FRONT") >/dev/null 2>&1 || true
    wait
    exit 0
  fi
  printf "."; sleep 3
done
echo; echo "A API não respondeu em 4 minutos. Veja o arquivo api.log." >&2
exit 1
