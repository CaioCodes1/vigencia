# Vigência

Backend que substitui a planilha de controle de contratos de empresas com
receita recorrente: guarda os contratos, **avisa antes de vencer**, gera as
cobranças, registra os pagamentos, mantém a trilha de auditoria e expõe um
painel gerencial.

**Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · Redis · RabbitMQ ·
Prometheus · Grafana · Loki · Testcontainers**

> **Estado: 8 fases de 8 concluídas.** 352 testes verdes (224 unitários + 128 de
> integração), 84% de cobertura de linha, 0 violações de Checkstyle.

---

## O problema

Empresas com contrato recorrente controlam vencimento em planilha. Planilha não
tem três coisas que este domínio exige:

- **tempo** — algo precisa acontecer numa data, sem ninguém clicar;
- **garantia** — a operação inteira dá certo, ou nenhuma parte dá;
- **memória** — quem fez, quando, de onde, e o que era antes.

O resultado é sempre o mesmo: contrato que vence em silêncio, cobrança
esquecida, e nenhuma métrica de renovação.

---

## O que o sistema faz

| | |
|---|---|
| **Contratos** | Máquina de estados com 9 transições, renovação encadeada (`previousContractId`), auto-renovação, varredura diária de vencimentos |
| **Cobranças** | Ativar o contrato gera as parcelas na mesma transação; pagamento parcial, estorno que marca em vez de apagar, varredura de inadimplência |
| **Avisos** | Régua D-30/15/7/1 e D+1/D+7, e-mail por SMTP ou log, reenvio manual, registro de tentativa e erro |
| **Segurança** | Argon2id, JWT RS256 de 15 min, refresh com rotação e detecção de reuso, RBAC com 33 permissões e 6 papéis, escopo por carteira, documento cifrado em repouso |
| **Auditoria** | Trilha append-only garantida por gatilho e `REVOKE`, preenchida por AOP, com o "antes", o "depois" e os campos alterados |
| **Painel** | Contratos, receita realizada × prevista, taxa de renovação, inadimplência — com cache no Redis por escopo |
| **Operação** | Métricas de negócio no Prometheus, log JSON no Loki, 4 dashboards do Grafana, alertas e health check da outbox |

---

## Rodar

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

```bash
cp .env.example .env      # e preencha as senhas
docker compose up -d      # Postgres 5434, Redis 6380, RabbitMQ 5673/15673
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Swagger (perfil `dev`) | http://localhost:8080/swagger-ui |
| Health | http://localhost:8080/actuator/health |
| RabbitMQ (painel) | http://localhost:15673 |

**Portas escolhidas para não brigar com os outros projetos deste workspace:**
5432 é do `stockflow`/`beacon-analytics`, 5433 e 8080 são do `billing-platform`.
Por isso o Postgres deste projeto fica na **5434** e, quando a app roda em
container, ela é publicada na **8081** (`APP_PORT`).

### A pilha de observabilidade

```bash
docker compose --profile observability up -d
```

| | URL | |
|---|---|---|
| Prometheus | http://localhost:9090 | métricas e alertas |
| Grafana | http://localhost:3001 | 4 dashboards provisionados por arquivo |
| Loki | http://localhost:3100 | log estruturado, via Promtail |

Fica atrás de um perfil de propósito: são mais quatro containers e ~700 MB. No
dia a dia não precisa.

O Prometheus raspa `host.docker.internal:8080` — a aplicação rodando **no
host**, que é o fluxo normal. Subindo a app pelo perfil `app`, troque o alvo por
`app:8080` em `docker/prometheus/prometheus.yml`.

### Rodar a app em container

```bash
docker compose --profile app up -d --build
```

---

## Testes

```bash
mvn test      # 224 unitários (*Test), segundos, sem Docker
mvn verify    # + 128 de integração (*IT) com Testcontainers, e o portão do JaCoCo
```

O nome define quando roda: `*Test` é Surefire, `*IT` é Failsafe. `mvn verify`
também reprova o build abaixo de **80% de cobertura de linha** (hoje: 84%),
ignorando DTO, mapper gerado, entity e config — contar isso infla o número sem
nenhum teste a mais.

Alguns que vale conhecer pelo nome:

| Teste | O que ele impede |
|---|---|
| `ArchitectureTest` | Domínio importando Spring/JPA/Jackson; ciclo entre módulos; `@Entity` fora da persistência |
| `DashboardIT.painel_do_vendedor_nao_deve_vazar_outra_carteira` | Vazamento de escopo pelo cache — o vendedor recebendo o painel do gestor |
| `AuditIT.tentativa_frustrada_nao_deve_ser_auditada` | A trilha registrar intenção em vez de fato (ordem do aspecto × transação) |
| `MetricsIT.nao_deve_ter_id_como_rotulo` | Cardinalidade de métrica derrubando o Prometheus |
| `ContractIT` · `BillingIT` | Renovação duplicada, parcela somando errado, pagamento contado duas vezes |

> **Nesta máquina, os testes de integração exigem dois ajustes** (Docker Engine
> 29 + docker-java antigo), os mesmos já aplicados para o `billing-platform`:
>
> | Ajuste | Onde | Valor |
> |---|---|---|
> | Versão da API | `~/.docker-java.properties` | `api.version=1.44` |
> | Endpoint | variável de usuário `DOCKER_HOST` | `npipe:////./pipe/docker_engine_linux` |
>
> Sem eles, todo `*IT` morre com *"Could not find a valid Docker environment"* —
> mesmo com `docker run` funcionando. Nenhum dos dois sobrevive a uma formatação.

---

## API

Todas as rotas sob `/api/v1`. Autorização por **permissão**, nunca por papel:
`contract:renew`, não `hasRole('MANAGER')`.

| Recurso | Rotas |
|---|---|
| **Auth** | `POST /auth/login` · `/auth/refresh` · `/auth/logout` · `GET /auth/me` · `POST /auth/change-password` |
| **Clientes** | `POST /clients` · `GET /clients` · `GET /clients/{id}` · `PATCH /clients/{id}` · `POST /clients/{id}/contacts` · `DELETE /clients/{id}` · `POST /clients/{id}/reactivate` |
| **Contratos** | `POST /contracts` · `GET /contracts` · `GET /contracts/{id}` · `PATCH /contracts/{id}` · `POST /contracts/{id}/activate` · `/renew` · `/cancel` · `/suspend` · `/resume` · `GET /contracts/expiring` · `GET /contracts/{id}/chain` |
| **Cobranças** | `POST /billings` · `GET /billings` · `GET /billings/overdue` · `GET /billings/{id}` · `POST /billings/{id}/payments` · `POST /billings/{id}/cancel` · `DELETE /payments/{id}` (estorno) |
| **Avisos** | `GET /notifications` · `POST /notifications/{id}/resend` |
| **Painel** | `GET /dashboard/summary` · `/revenue` · `/renewal-rate` · `/delinquent-clients` · `/expiring-timeline` |
| **Auditoria** | `GET /audit-logs` · `GET /audit-logs/{id}` |
| **Operação** | `GET /actuator/health` (público) · `/actuator/prometheus` (rede interna ou `system:monitor`) |

Dois contratos de API que valem a leitura:

- **`Idempotency-Key` é obrigatório** em renovação e em registro de pagamento.
  Gateway reenvia webhook, e o mesmo PIX contado duas vezes é dinheiro que a
  empresa acha que recebeu. Repetir a chave devolve **200** com o mesmo
  resultado; a primeira chamada devolve **201**.
- **Fora da sua carteira, a resposta é 404 — nunca 403.** Um 403 confirmaria que
  aquele id existe, e daria para enumerar a base inteira trocando o id na URL.

---

## Estrutura

```
src/main/java/com/caiocodes/vigencia/
├── shared/          # Money, DateRange, Document, outbox, métricas, erro HTTP
├── iam/             # usuários, papéis, permissões, tokens        (fase 2)
├── client/          # clientes e contatos                          (fase 3)
├── contract/        # contratos, renovação, vencimentos            (fase 4)
├── billing/         # cobranças e pagamentos                       (fase 5)
├── notification/    # avisos D-30/15/7/1 e régua de cobrança       (fase 6)
├── audit/           # trilha append-only, preenchida por AOP       (fase 7)
└── reporting/       # painel — sem domínio, SQL na mão             (fase 7)
```

Dentro de cada módulo: `domain` (Java puro) → `application` (casos de uso) →
`infrastructure` (JPA, mensageria, scheduler) → `web` (controllers).

**A dependência sempre aponta para dentro.** O `domain` não importa Spring, JPA
nem Jackson — e isso não é convenção verbal: o `ArchitectureTest` quebra o build
se alguém tentar. Módulos se referenciam por **UUID**, nunca por objeto, e
conversam por portas declaradas por quem *precisa* e implementadas por quem
*sabe*.

Dois módulos **não têm camada de domínio**, de propósito: `reporting` e `audit`.
Relatório é `SELECT ... GROUP BY`, e auditoria nasce pronta e nunca muda — não há
invariante para proteger, e forçar agregado ali só produziria cerimônia e query
lenta.

---

## Decisões que não são óbvias pelo código

| Decisão | Por quê |
|---|---|
| Domínio separado da `@Entity` JPA | `Contract.renew()` testável em 2 ms, sem framework. Custa mais um mapper por agregado |
| Renovar **cria** um contrato novo | Preserva o histórico de valor; é o que torna a taxa de renovação calculável |
| Índice único **parcial** (`WHERE status='ACTIVE'`) | A regra vira garantia física; um `SELECT` antes do `INSERT` não sobrevive a duas requisições simultâneas |
| `Clock` injetado, nunca `LocalDate.now()` no domínio | Sem isso, testar "vence em 30 dias" exigiria mexer no relógio da máquina |
| `Money` em vez de `BigDecimal` solto | Impede somar moedas diferentes; a sobra de centavos cai na última parcela |
| Outbox para os eventos | Não existe transação distribuída entre Postgres e RabbitMQ; o evento é gravado no mesmo commit |
| `INSERT ... ON CONFLICT DO NOTHING`, não `try/catch` | No Postgres, o primeiro comando que falha **envenena a transação inteira**; capturar a exceção não desfaz isso |
| ShedLock em todo job, **menos** no relay da outbox | A trava exclui; o `FOR UPDATE SKIP LOCKED` particiona. Pôr a trava por cima desfaz o motivo de o `SKIP LOCKED` existir |
| Auditoria imutável por **gatilho e** `REVOKE` | Superusuário ignora `GRANT` — nos testes, um `REVOKE` sozinho passaria verde sem verificar nada |
| O escopo do painel é um **tipo**, e entra na chave do cache | Chave fixa faria o vendedor receber o painel do gestor: vazamento de autorização que não quebra tela nenhuma |
| Serializador de cache **tipado**, sem *default typing* | Desserialização polimórfica de conteúdo vindo do Redis é a família de CVE mais explorada do ecossistema Java |
| `forward-headers-strategy: none` | Em `framework`, o Spring reescreve `getRemoteAddr()` a partir do `X-Forwarded-For` — e o IP da auditoria passa a ser o que o cliente escolher |
| Gauge lê memória, não o banco | Consulta dentro do gauge roda a cada scrape, para sempre. Alvo de monitoramento que pesa no banco acaba desligado no primeiro incidente |
| `VARCHAR(n)` em vez de `TEXT`/`citext`/`INET` | Com `ddl-auto: validate`, o Hibernate reprova o schema em tipos que não conhece e a aplicação nem sobe |
| `@Version` como `Long`, e o `save` devolve o agregado recebido | Com `long` primitivo o Spring Data sempre faz `merge`, e o merge troca as coleções por referências não inicializadas |
| `noRollbackFor` no login | Sem isso, o rollback desfaz o incremento do contador e o bloqueio por força bruta nunca acontece |
| `f_unaccent` como função IMMUTABLE | `unaccent()` é STABLE e não pode ser indexado; sem o wrapper, o índice de busca por nome não é criado |

As 27 decisões arquiteturais completas — com contexto, alternativas descartadas
e reversibilidade — estão registradas como ADRs num cofre do Obsidian mantido
fora do repositório.

---

## CI/CD

`.github/workflows/ci.yml` — em todo PR, em paralelo: Checkstyle, testes
unitários e a varredura de segurança (Gitleaks no histórico, CodeQL,
Dependency-Check e Trivy na imagem). Os testes de integração e o portão de
cobertura vêm depois. Nada entra na `main` sem passar por tudo.

`.github/workflows/cd.yml` — a imagem vai para o GHCR assinada com Cosign;
`main` publica em staging; **produção só por tag `v*`**, com aprovação manual e
backup do banco antes da migração. Os dois jobs de deploy ficam desligados até
`STAGING_HOST` / `PRODUCTION_HOST` existirem nas variáveis do repositório — sem
isso, um repositório sem servidor teria um CD vermelho permanente.

---

## Licença

MIT.
