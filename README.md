# Contract Renewal & Billing Automation Platform

Backend que substitui a planilha de controle de contratos de empresas com
receita recorrente: guarda os contratos, **avisa antes de vencer**, gera as
cobranças, registra os pagamentos, mantém a trilha de auditoria e expõe um
painel gerencial.

**Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · Redis · RabbitMQ ·
Testcontainers**

> **Estado: fase 2 de 8 — IAM e segurança.** O roadmap está em
> `18 — Roadmap de Implementação` no cofre do Obsidian.
>
> **Fase 1 (esqueleto):** build, migrações, value objects (`Money`, `DateRange`,
> `Document`), tratamento global de erro, correlação por `traceId`, health
> checks e o teste de arquitetura.
>
> **Fase 2 (autenticação e autorização):** login com Argon2id, JWT RS256 de 15
> minutos, refresh token opaco com rotação e detecção de reuso, logout com
> denylist no Redis, bloqueio de conta por tentativas, rate limit por IP, RBAC
> com 30 permissões e 6 papéis, e `deny by default` em toda a API.
>
> **Ainda não existe:** cliente, contrato, cobrança, notificação e dashboard —
> fases 3 a 7.
>
> **121 testes verdes** (82 unitários + 39 de integração), 0 violações de
> Checkstyle.

## Endpoints disponíveis hoje

| Método | Rota | Quem pode |
|---|---|---|
| POST | `/api/v1/auth/login` | público (rate limit por IP) |
| POST | `/api/v1/auth/refresh` | público (rate limit por IP) |
| POST | `/api/v1/auth/logout` | autenticado |
| GET | `/api/v1/auth/me` | autenticado |
| POST | `/api/v1/auth/change-password` | autenticado |
| GET | `/api/v1/users` · `/api/v1/users/{id}` | permissão `user:read` |
| GET | `/actuator/health` | público |

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

## Rodar

Pré-requisitos: JDK 21, Maven 3.9+ e Docker.

```bash
cp .env.example .env      # e preencha as senhas
docker compose up -d      # Postgres 5434, Redis 6380, RabbitMQ 5673/15673
```

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| | URL |
|---|---|
| API | http://localhost:8080/api/v1 |
| Health | http://localhost:8080/actuator/health |
| Swagger (perfil `dev`) | http://localhost:8080/swagger-ui |
| RabbitMQ (painel) | http://localhost:15673 |

**Portas escolhidas para não brigar com os outros projetos deste workspace:**
5432 é do `stockflow`/`beacon-analytics`, 5433 e 8080 são do `billing-platform`.
Por isso o Postgres deste projeto fica na **5434** e, quando a app roda em
container, ela é publicada na **8081** (`APP_PORT`). Rodando pela IDE ou por
`mvn spring-boot:run`, a app usa a 8080 — não suba os dois ao mesmo tempo.

### Testes

```bash
mvn test
```

```bash
mvn verify
```

`mvn test` roda só os unitários (`*Test`) — segundos, sem Docker.
`mvn verify` inclui os de integração (`*IT`), que sobem um PostgreSQL real via
Testcontainers.

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

### Rodar a app em container

```bash
docker compose --profile app up -d --build
```

---

## Estrutura

```
src/main/java/com/caiocodes/crbap/
├── shared/          # Money, DateRange, Document, exceções, erro HTTP, traceId
├── iam/             # usuários, papéis, permissões, tokens      (fase 2)
├── client/          # clientes e contatos                        (fase 3)
├── contract/        # contratos, renovação, vencimentos          (fase 4)
├── billing/         # cobranças e pagamentos                     (fase 5)
├── notification/    # avisos D-30/15/7/1 e régua de cobrança     (fase 6)
└── reporting/       # dashboard — só leitura, SQL na mão         (fase 7)
```

Dentro de cada módulo: `domain` (Java puro) → `application` (casos de uso) →
`infrastructure` (JPA, mensageria, scheduler) → `web` (controllers).

**A dependência sempre aponta para dentro.** O `domain` não importa Spring, JPA
nem Jackson — e isso não é convenção verbal: `ArchitectureTest` quebra o build
se alguém tentar.

---

## Decisões que não são óbvias pelo código

| Decisão | Por quê |
|---|---|
| Domínio separado da `@Entity` JPA | `Contract.renew()` testável em 2 ms, sem framework. Custa mais um mapper por agregado |
| `VARCHAR(n)` em vez de `TEXT`/`citext`/`INET` | Com `ddl-auto: validate`, o Hibernate reprova o schema em tipos que não conhece e a aplicação nem sobe. A unicidade case-insensitive vira índice sobre `lower(email)` |
| `@Version` como `Long`, e o `save` devolve o agregado recebido | Com `long` primitivo o Spring Data sempre faz `merge`, e o merge troca as coleções por referências não inicializadas — `LazyInitializationException` na hora de mapear de volta |
| `@Transactional(noRollbackFor = InvalidCredentialsException.class)` no login | Sem isso, o rollback desfaz o incremento do contador de tentativas e o bloqueio por força bruta nunca acontece |
| `@ExceptionHandler(AccessDeniedException.class)` explícito | O `@RestControllerAdvice` intercepta antes do `AccessDeniedHandler` do Spring Security; sem ele, todo 403 vira 500 |
| Renovar **cria** um contrato novo | Preserva histórico de valor; é o que torna a taxa de renovação calculável |
| Índice único **parcial** (`WHERE status='ACTIVE'`) | A regra "documento único entre ativos" vira garantia física; um `SELECT` antes do `INSERT` não sobrevive a duas requisições simultâneas |
| `Clock` injetado, nunca `LocalDate.now()` no domínio | Sem isso, testar "vence em 30 dias" exigiria mexer no relógio da máquina |
| `Money` em vez de `BigDecimal` solto | Impede somar moedas diferentes e torna `equals` seguro (escala normalizada) |
| Outbox para os eventos (fase 6) | Não existe transação distribuída entre Postgres e RabbitMQ; o evento é gravado no mesmo commit |
| `f_unaccent` como função IMMUTABLE | `unaccent()` é STABLE e não pode ser indexado; sem o wrapper, o índice de busca por nome não é criado |
| Postgres na 5434 | Convivência com os outros projetos do workspace |

A documentação completa — arquitetura, modelagem, API, segurança, testes,
infra, ADRs e roadmap — está no cofre do Obsidian em
`E:\huush automations\Contract Renewal Platform\`.

---

## Licença

MIT.
