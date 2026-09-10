#!/usr/bin/env bash
# Smoke test pós-deploy: seis perguntas, nenhuma delas sobre regra de negócio.
#
# O QUE ELE NÃO É: uma segunda suíte de testes. Regra de negócio já foi
# verificada 342 vezes no CI, com banco de verdade. Rodar isso de novo
# contra produção só cria dado de mentira na base do cliente.
#
# O QUE ELE É: a checagem de que ESTE artefato, NESTE servidor, com ESTA
# configuração, está de pé — que é exatamente o que o CI não consegue
# testar. As falhas que ele pega são de ambiente: variável faltando,
# migração que não rodou, porta errada, certificado vencido.
#
# Uso: ./scripts/smoke-test.sh https://staging.exemplo.com
set -euo pipefail

BASE_URL="${1:?informe a URL base}"
FALHAS=0

verificar() {
  local descricao="$1" esperado="$2" url="$3"
  shift 3
  local codigo
  codigo=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@" "$url" || echo "000")

  if [ "$codigo" = "$esperado" ]; then
    printf '  OK   %-52s %s\n' "$descricao" "$codigo"
  else
    printf '  FALHA %-51s esperado %s, veio %s\n' "$descricao" "$esperado" "$codigo"
    FALHAS=$((FALHAS + 1))
  fi
}

echo "Smoke test em ${BASE_URL}"

# 1. Está de pé e terminou de subir.
verificar "readiness responde" 200 "${BASE_URL}/actuator/health/readiness"

# 2. A migração rodou. Sem isto, a aplicação sobe e quebra na primeira
#    consulta — e o /health não acusa nada.
verificar "banco acessível (liveness)" 200 "${BASE_URL}/actuator/health/liveness"

# 3. A rota protegida ainda está protegida. É a verificação mais
#    importante da lista: um deploy com a segurança desligada responde
#    200 em tudo e passaria por "saudável".
verificar "rota protegida exige token" 401 "${BASE_URL}/api/v1/contracts"

# 4. Credencial errada não entra.
verificar "login com senha errada é recusado" 401 \
  "${BASE_URL}/api/v1/auth/login" \
  -X POST -H 'Content-Type: application/json' \
  -d '{"email":"ninguem@exemplo.com","password":"senha-errada-de-proposito"}'

# 5. As métricas não estão abertas para a internet. O runner do GitHub
#    chama de fora, então a resposta correta é recusar.
verificar "métricas fechadas para fora" 401 "${BASE_URL}/actuator/prometheus"

# 6. Documentação desligada em produção (springdoc.api-docs.enabled=false).
#    404 é o esperado; 200 significa perfil errado no deploy.
verificar "swagger desligado" 404 "${BASE_URL}/v3/api-docs"

echo ""
if [ "$FALHAS" -gt 0 ]; then
  echo "${FALHAS} verificação(ões) falharam."
  exit 1
fi
echo "Tudo certo."
