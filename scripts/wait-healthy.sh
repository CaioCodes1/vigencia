#!/usr/bin/env bash
# Espera a aplicação ficar PRONTA (readiness), não apenas viva.
#
# A diferença importa: /health/liveness responde OK assim que o processo
# sobe, antes de o Flyway migrar e antes de o pool conectar. Fazer smoke
# test contra o liveness testa uma aplicação que ainda não terminou de
# nascer — e falha de forma intermitente, que é o pior tipo de falha de
# pipeline.
#
# Uso: ./scripts/wait-healthy.sh https://staging.exemplo.com [tentativas]
set -euo pipefail

BASE_URL="${1:?informe a URL base}"
TENTATIVAS="${2:-30}"
INTERVALO=10

echo "Esperando ${BASE_URL}/actuator/health/readiness ficar UP..."

for i in $(seq 1 "$TENTATIVAS"); do
  if curl -fsS --max-time 5 "${BASE_URL}/actuator/health/readiness" | grep -q '"status":"UP"'; then
    echo "Pronto na tentativa ${i}."
    exit 0
  fi
  printf '.'
  sleep "$INTERVALO"
done

echo ""
echo "Não ficou pronto em $((TENTATIVAS * INTERVALO))s."
# Uma última chamada SEM -f, para o log do CI mostrar o corpo da resposta.
# Um deploy que falha sem dizer por quê custa a mesma investigação duas
# vezes: no pipeline e na máquina de quem for olhar.
curl -sS --max-time 5 "${BASE_URL}/actuator/health/readiness" || true
exit 1
