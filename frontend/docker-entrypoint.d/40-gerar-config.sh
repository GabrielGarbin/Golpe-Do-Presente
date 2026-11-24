#!/bin/sh
# Escreve o config.js a partir das variáveis de ambiente do contêiner.
set -eu
cat > /usr/share/nginx/html/config.js << CONFIG
window.APP_CONFIG = {
  API_URL: '${API_URL}',
  TIMEOUT_MS: ${TIMEOUT_MS:-12000},
  LIMITE_SUSPEITA: ${LIMITE_SUSPEITA:-1.2}
};
CONFIG
echo "config.js gerado: API_URL=${API_URL}"
