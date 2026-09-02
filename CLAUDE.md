# contract-renewal-platform

Plataforma de renovação de contratos e automação de cobrança. **Segundo projeto
Java do workspace** (o primeiro é o `billing-platform`).

Java 21 · Spring Boot 3.5.3 · PostgreSQL 16 · Flyway · MapStruct ·
Testcontainers. Redis e RabbitMQ já estão no `compose.yaml`, mas entram no
código nas fases 2 e 6.

> **Estado: fases 1 e 2 de 8 concluídas (01–02/09/2026).** 121 testes verdes
> (82 unitários + 39 de integração), 0 violações de Checkstyle.
>
> - **Fase 1** — build, migrações V1/V2, value objects (`Money`, `DateRange`,
>   `Document`), erro padronizado, `traceId`, health checks, ArchUnit.
> - **Fase 2** — IAM completo: Argon2id, JWT RS256, refresh com rotação e
>   detecção de reuso, logout com denylist no Redis, bloqueio de conta, rate
>   limit, RBAC (30 permissões, 6 papéis), `deny by default`.
>
> **Próxima: fase 3 (clientes).** Cliente, contrato, cobrança, notificação e
> dashboard ainda não existem.

## Rodar

Sem entrada no `.claude/launch.json` da raiz (API pura, sem front).

```bash
docker compose up -d      # infra: Postgres 5434, Redis 6380, RabbitMQ 5673
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test                  # unitários, sem Docker
mvn verify                # inclui os *IT (Testcontainers)
```

**Portas:** Postgres na **5434** e app em container na **8081**, de propósito —
5432 é do `stockflow`/`beacon-analytics`, 5433 e 8080 são do `billing-platform`.
Rodando pela IDE, a app usa a 8080; não subir junto com o `billing-platform`.

Os dois ajustes de Testcontainers × Engine 29 (`~/.docker-java.properties` com
`api.version=1.44` e `DOCKER_HOST=npipe:////./pipe/docker_engine_linux`) são os
mesmos do `billing-platform` e já estavam aplicados. **Não sobrevivem a
formatação.**

## Como este projeto difere do `billing-platform`

Os dois mexem com cobrança recorrente, e a semelhança é proposital — este é o
passo seguinte, não uma repetição:

| | billing-platform | contract-renewal-platform |
|---|---|---|
| Organização | Pacote por feature, camadas `controller/service/repository/entity` | Clean Architecture: `domain` puro separado da `@Entity`, com portas e adaptadores |
| Centro do domínio | Assinatura e fatura | **Contrato e sua renovação encadeada** (`previousContractId`) |
| Mensageria | Outbox + e-mail | Outbox + **RabbitMQ** com retry, DLQ e consumidor idempotente |
| Autorização | Papel | **Permissão fina** (`contract:renew`) + escopo por carteira |
| Garantias de arquitetura | Revisão | **ArchUnit** quebrando o build |
| Observabilidade | Actuator | Prometheus, Grafana, Loki e alerta de "o job parou" |

Se a dúvida for "isso não é o mesmo projeto de novo?": o que se aprende aqui é
Clean Architecture com domínio isolado, mensageria com garantia de entrega e
observabilidade — nada disso existe no `billing-platform`.

## Decisões que não são óbvias pelo código

Todas justificadas no cofre do Obsidian, em
`E:\huush automations\Contract Renewal Platform\` (22 notas: arquitetura,
modelagem, API, segurança, testes, DevOps, 12 ADRs e os roadmaps). As que mais
geram "conserto" indevido:

- **O domínio não importa Spring nem JPA.** Não é preciosismo: é o que faz
  `mvn test` rodar em segundos. Quem garante é o `ArchitectureTest` — se ele
  acusar violação, o certo é mover a classe, não afrouxar a regra.
- **`allowEmptyShould(true)` no `ArchitectureTest`** existe porque várias regras
  ainda não têm alvo (não há `@Entity` nem controller). **Remover conforme cada
  fase preencher o pacote** — deixar para sempre esconde regra que parou de valer.
- **`f_unaccent` na V1.** O `unaccent()` nativo é `STABLE`, e índice exige
  `IMMUTABLE`. Sem esse wrapper, o `CREATE INDEX` de V2 falha. Não é
  duplicação inútil.
- **`ck_clients_deact`** amarra `status = 'INACTIVE'` a `deactivated_at NOT NULL`.
  Um `UPDATE` que muda só o status é recusado pelo banco — de propósito.
- **Índice único parcial** (`WHERE status = 'ACTIVE'`): a regra "documento único
  entre ativos" mora no banco porque um `SELECT` antes do `INSERT` não sobrevive
  a duas requisições simultâneas.
- **`Money` normaliza a escala para 2 na construção.** Sem isso,
  `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` é `false` e o
  `equals` do record mentiria.
- **`Clock` é bean.** Nada de `LocalDate.now()` dentro do domínio: as janelas de
  aviso (D-30/15/7/1) só são testáveis com o relógio controlado.
- **AMQP ainda não está no `pom.xml`** (fase 6). Dependência entra quando existe
  código que a use — o starter derruba o health check enquanto o serviço não sobe.
- **`VARCHAR(n)`, nunca `TEXT`/`citext`/`INET`.** Com `ddl-auto: validate` o
  Hibernate compara o tipo de cada coluna com o que a entidade declara e
  reprova o schema inteiro no primeiro desencontro — a aplicação **não sobe**.
  Toda coluna de texto tem tamanho explícito na migração **e** `length` igual na
  entidade. A unicidade case-insensitive do e-mail é um índice sobre
  `lower(email)`, não `citext`.
- **`@Version` é `Long`, não `long`.** Primitivo nunca é nulo, então o Spring
  Data considera toda entidade "já existente" e o `save` vira `merge` — que
  troca as coleções `@ManyToMany` por referências não inicializadas.
- **Os adaptadores de repositório devolvem o agregado recebido**, não o retorno
  do `jpa.save()`. Remapear o retorno do merge estoura
  `LazyInitializationException` fora de transação. Não é descuido: está
  comentado no código.
- **`noRollbackFor` no login e no refresh.** O incremento do contador de
  tentativas e a revogação da família de tokens precisam sobreviver à exceção
  que sobe logo depois. Sem isso o bloqueio por força bruta simplesmente não
  funciona, e nenhum teste unitário pega.
- **`@ExceptionHandler(AccessDeniedException.class)` no handler global.** Sem
  ele, o `@RestControllerAdvice` intercepta antes do `AccessDeniedHandler` do
  Spring Security e todo 403 vira 500.
- **Chaves RSA efêmeras fora de produção.** Se `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`
  não vierem do ambiente, um par é gerado na subida com log `WARN`; no perfil
  `prod` a aplicação **falha ao subir**. Ver ADR-014.
- **Rate limit é contador de janela fixa (`INCR`+`EXPIRE`), não Bucket4j.**
  Ver ADR-013 — a rajada de virada de janela é aceita conscientemente.
- **JaCoCo com `jacoco.check.skip=true`.** A meta de 80% liga na fase 4, quando
  existir lógica suficiente para ela significar alguma coisa.

## Convenções

Seguem as da raiz (`E:\projetos\CLAUDE.md`): documentação, comentários e
mensagens de erro em português; código em inglês; commits `tipo: descrição`.

Específicas deste projeto:

- **`*Test` = unitário** (Surefire, sem Docker); **`*IT` = integração**
  (Failsafe, Testcontainers). O nome define quando roda.
- Nome de teste descreve **comportamento**, não método:
  `deve_recusar_renovacao_de_contrato_cancelado`.
- Uma migração já mergeada **nunca** é editada — cria-se a próxima. O Flyway
  valida checksum e o boot falha, que é o comportamento desejado.
- Um caso de uso por classe (`RenewContractUseCase`), não um service de 900
  linhas.
- `@Transactional` só na camada `application`. Nunca no controller, nunca no
  repositório.

## Pendências

- **`git init` + primeiro commit + publicar em `CaioCodes1/`** — maior risco
  hoje: o projeto inteiro (duas fases) vive só em disco local, exatamente a
  situação do `bank-api`.
- Fase 3 (clientes) é a próxima: agregado `Client`, criptografia de campo
  (AES-GCM) e o blind index HMAC para busca.
- Falta gestão de usuários pela API (`POST /users`, conceder/revogar papéis).
  Hoje só existe leitura; usuário novo depende do `AdminBootstrap`.
- O limite de rate limit **por e-mail** (além do por IP) ainda não existe.
- `allowEmptyShould(true)` no `ArchitectureTest`: remover das regras cujo alvo
  já passou a existir (`@RestController` e `*UseCase` já existem desde a fase 2).
- Decidir entre o roadmap completo (12–14 semanas) e a versão reduzida de 4
  semanas descrita no fim da nota 18.
