# contract-renewal-platform

Plataforma de renovação de contratos e automação de cobrança. **Segundo projeto
Java do workspace** (o primeiro é o `billing-platform`).

Java 21 · Spring Boot 3.5.3 · PostgreSQL 16 · Flyway · MapStruct ·
Testcontainers. Redis e RabbitMQ já estão no `compose.yaml`, mas entram no
código nas fases 2 e 6.

> **Estado: fases 1 a 6 de 8 concluídas (01–04/09/2026).** 310 testes verdes
> (207 unitários + 103 de integração), 0 violações de Checkstyle.
>
> - **Fase 1** — build, migrações V1/V2, value objects (`Money`, `DateRange`,
>   `Document`), erro padronizado, `traceId`, health checks, ArchUnit.
> - **Fase 2** — IAM completo: Argon2id, JWT RS256, refresh com rotação e
>   detecção de reuso, logout com denylist no Redis, bloqueio de conta, rate
>   limit, RBAC (32 permissões, 6 papéis), `deny by default`.
> - **Fase 3** — clientes: agregado `Client` com contatos, documento cifrado em
>   repouso (AES-256-GCM) + índice cego HMAC para busca e unicidade, escopo por
>   carteira, busca sem acento e desativação lógica com recadastro.
> - **Fase 4** — contratos: agregado `Contract` com as 9 transições da máquina
>   de estados, renovação encadeada com `Idempotency-Key` e lock pessimista,
>   `/expiring` com faixas, `/chain` por CTE recursiva, varredura diária de
>   vencimentos e **outbox** (tabela, gravação transacional e relay).
> - **Fase 5** — cobranças: ativar contrato gera as parcelas na mesma transação
>   (ADR-006), pagamento parcial com `Idempotency-Key` e lock, estorno que marca
>   em vez de apagar, varredura diária de inadimplência e o cálculo de parcelas
>   com a sobra de centavos na última.
> - **Fase 6** — notificações: régua D-30/15/7/1 e D+1/D+7, envio separado do
>   agendamento, log de envio com reenvio manual, auto-renovação antes da
>   expiração, ShedLock nos jobs, RabbitMQ com DLQ e os consumidores de contrato
>   e de cobrança. **É aqui que o produto passa a existir** — tudo antes disto a
>   planilha também fazia, mal; o que ela nunca fez foi avisar sozinha.
>
> **Próxima: fase 7 (dashboard e auditoria).** `AuditLog` ainda não existe.

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
- **`allowEmptyShould(true)` foi removido do `ArchitectureTest` na fase 4.**
  Todo alvo já existe; se uma regra voltar a ficar vazia, o build quebra — que
  é o comportamento desejado, porque regra vazia é regra que parou de valer sem
  ninguém perceber. **Não reintroduzir para "consertar" um teste vermelho.**
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
- **O documento do cliente é cifrado (AES-256-GCM) e indexado por HMAC.** São
  duas colunas: `document_enc` (não pesquisável) e `document_index` (busca e
  unicidade). Perder `DATA_ENCRYPTION_KEY` = perder os documentos; o backup da
  chave é tão crítico quanto o do banco.
- **`accountManagerId` não é filtro de query em `/clients`.** Aceitar seria dar
  ao vendedor exatamente o parâmetro para ler a carteira alheia. O escopo vem do
  token via a porta `CurrentUser` (declarada em `shared/application`,
  implementada pelo `iam`). Vale também na escrita: o `accountManagerId` enviado
  no corpo é ignorado para quem não tem `client:read_all`.
- **A listagem devolve `ClientListItem`, não o agregado.** Carregar `Client`
  inteiro numa página de 20 traria 20 listas de contatos que a tela não mostra.
- **`NoContractsYetAdapter` foi apagado na fase 4.** Quem implementa
  `ClientContractsPort` agora é `contract/infrastructure/ClientContractsAdapter`.
- **JaCoCo com `jacoco.check.skip=true`.** A meta de 80% liga na fase 5, quando
  existir lógica suficiente para ela significar alguma coisa.

Fase 4 (contratos):

- **`Contract.renew()` devolve um agregado novo e não altera as datas do atual.**
  O contrato anterior tem que continuar existindo com o valor e o período que
  teve — é exigência fiscal, não preferência de modelagem. Quem grava os dois é
  o caso de uso, na mesma transação.
- **Três defesas diferentes contra renovação duplicada, e cada uma pega um caso
  distinto.** `Idempotency-Key` gravada no sucessor resolve o clique duplo com
  a primeira já commitada; `findByIdForUpdate` (`SELECT ... FOR UPDATE`) resolve
  as duas simultâneas, quando a chave ainda não existe para ser encontrada; o
  índice `uk_contracts_one_successor` é a rede final no banco. Remover qualquer
  uma delas parece funcionar em teste manual.
- **Renovar responde 201 na primeira chamada e 200 na repetida.** Não é
  detalhe: é como o cliente HTTP sabe se aquele contrato nasceu nesta chamada.
- **`ContractExpirer` é uma classe separada do `ExpireContractsUseCase` por
  causa do proxy do Spring.** `@Transactional(REQUIRES_NEW)` num método chamado
  de outro método da mesma classe é **silenciosamente ignorado** — o lote
  inteiro voltaria a compartilhar uma transação. Não juntar as duas.
- **`ExpireContractsUseCase.execute()` não tem `@Transactional`**, de propósito:
  ele só percorre o lote. Anotá-lo cria a transação longa que o desenho evita.
- **`DomainEventRecorder` usa `Propagation.MANDATORY`.** Falha se for chamado
  fora de transação — é o que garante, em execução, que o evento e a mudança do
  agregado caiam no mesmo commit. Sem isso a outbox não resolve nada.
- **O relay usa `FOR UPDATE SKIP LOCKED`.** Com duas instâncias no ar, sem o
  `SKIP LOCKED` a segunda travaria esperando a primeira ou publicaria o mesmo
  evento duas vezes. A entrega é **ao menos uma vez** — todo consumidor da fase
  6 tem de ser idempotente.
- **Os eventos carregam só tipos primitivos** (`BigDecimal`, `String`,
  `LocalDate`), nunca `Money` ou `DateRange`. O evento é a fronteira com quem
  consome; carregar value object do domínio faria refatoração interna quebrar
  consumidor externo.
- **O escopo de carteira do contrato sai de um join com `clients`**, porque
  quem tem gestor é o cliente. Por isso a busca é nativa: filtrar em memória
  depois traria página incompleta e contagem total errada.
- **`ContractFinder` centraliza "fora do escopo responde 404, nunca 403".**
  Repetido em cada caso de uso, bastaria um esquecimento num endpoint novo para
  abrir o IDOR que os outros seis fecham.
- **`crbap.jobs.enabled=false` no perfil de teste.** Com o agendador ligado, um
  job dispara no meio de um teste e muda o dado que ele está conferindo. Os
  testes chamam o caso de uso direto, com o `Clock` controlado.
- **`created_by` é `ON DELETE SET NULL`.** Sem isso, apagar um usuário é
  bloqueado por um contrato de dois anos atrás — e a limpeza de usuários dos
  testes de integração quebra, porque o `@BeforeEach` da superclasse roda antes
  do da subclasse.

Fase 5 (cobranças):

- **`BillingCycle` mora em `shared/domain`, não em `contract/domain`.** Dois
  módulos precisam dela: contrato para saber sua periodicidade, cobrança para
  calcular parcelas. Foi **movida na fase 5** — não devolver.
- **`ClientDirectory` (em `shared/application`) substituiu o `ContractClientPort`.**
  Contratos e cobranças precisam exatamente dos mesmos quatro campos do cliente;
  duas portas idênticas seriam duas implementações para manter em sincronia.
  Quem implementa é `client/infrastructure/ClientDirectoryAdapter`.
- **Ativar contrato gera as cobranças na MESMA transação** (ADR-006). É a
  exceção documentada à regra de um agregado por transação: contrato ativo sem
  cobrança é a empresa parar de faturar sem ninguém perceber. O
  `GenerateContractBillingsUseCase` usa `Propagation.MANDATORY` para que isso
  não possa ser afrouxado por acidente.
- **O saldo da cobrança nunca é coluna.** É sempre `valor − soma dos pagamentos
  não estornados`, e o `status` é recalculado por uma função só
  (`recalculateStatus`), chamada por registrar, estornar e cancelar. Guardar o
  saldo criaria duas fontes de verdade, e o primeiro estorno que esquecesse de
  atualizar faria a empresa cobrar quem já pagou.
- **Estorno marca, não apaga.** `refunded = true` e o valor sai do saldo; a
  linha continua. Apagar significaria dinheiro que entrou e sumiu do histórico.
- **`Money.split(n)` joga a sobra de centavos na ÚLTIMA parcela**, e há um teste
  parametrizado conferindo que a soma bate para vários valores. A última, e não
  a primeira, porque a primeira é a que o cliente vê ao assinar.
- **O calculador limita as parcelas ao valor em centavos.** R$ 0,01 em 12
  parcelas daria onze de R$ 0,00, e cobrança de valor zero é recusada pelo
  agregado — o cálculo não pode produzi-la para o `INSERT` estourar depois.
- **`Idempotency-Key` é obrigatória no pagamento** (na renovação é opcional).
  Gateway reenvia webhook, e o mesmo PIX contado duas vezes é dinheiro que a
  empresa acha que recebeu. O índice `uk_payments_idem` **não** é parcial.
- **Cobrança com pagamento não é cancelável** — o caminho é o estorno. Cancelar
  por cima esconderia um pagamento recebido.
- **Cancelar contrato cancela só as cobranças futuras.** As vencidas continuam
  de pé: cancelar contrato não perdoa dívida.
- **`findByIdForUpdate` não usa `EntityGraph`.** O Hibernate transforma o grafo
  num `LEFT JOIN` e o Postgres recusa `FOR UPDATE` sobre o lado nulável de um
  outer join. Trava-se a cobrança e os pagamentos vêm na leitura seguinte,
  dentro da mesma transação.
- **`markOverdue` devolve `false` em vez de lançar** quando não se aplica: é
  chamado em lote sobre milhares de cobranças, e "essa aqui não" é o caso
  normal. `registerPayment` lança, porque ali existe um humano esperando.
- **A limpeza dos testes de integração é toda do `AbstractIamIntegrationTest`**,
  na ordem filhos → pais. Cada classe ter o seu `@BeforeEach` parou de funcionar
  quando cobranças passaram a referenciar contratos e clientes com
  `ON DELETE RESTRICT` — o JUnit roda o da superclasse primeiro.
- **`SALES` ganhou `billing:read` (sem `billing:read_all`).** Sem isso nenhum
  papel exercitava o escopo de carteira em cobranças, e a regra existia sem
  ninguém poder alcançá-la.

Fase 6 (notificações):

- **`scheduleIfAbsent` usa `INSERT ... ON CONFLICT DO NOTHING`, não
  `try/catch`.** No Postgres, o primeiro comando que falha **envenena a
  transação inteira** — todo comando seguinte responde `current transaction is
  aborted`. Capturar `DataIntegrityViolationException` não desfaz isso. O bug
  real: o aviso vai para dois destinatários; na segunda rodada do job o primeiro
  `INSERT` era "tratado" e o segundo derrubava a varredura. **Não voltar para o
  try/catch** — ver ADR-022.
- **Agendar e enviar são jobs separados** (ADR-020). O SMTP fora do ar às 3h não
  pode impedir o sistema de *saber* quem precisa ser avisado.
- **A janela é exata, nunca `<= 30`.** Com "menor ou igual", o mesmo aviso sai
  todo dia por trinta dias — e passa em todo teste de caso feliz.
- **O `recipient` faz parte da chave de unicidade.** O mesmo aviso vai para o
  contato do cliente *e* para o gestor: duas linhas legítimas da mesma janela.
- **Três índices de deduplicação, não um** — por janela (contrato), por janela
  (cobrança, sem `PAYMENT_RECEIVED`) e por `payload->>'eventId'`. Dois pagamentos
  parciais **devem** gerar dois recibos; "vencida há 7 dias" não pode repetir.
- **O relay da outbox NÃO tem `@SchedulerLock`**, ao contrário dos outros jobs.
  A trava é exclusão; o `FOR UPDATE SKIP LOCKED` é partição. Pôr a trava por
  cima serializa o relay e desfaz o motivo de o `SKIP LOCKED` existir. Isto foi
  escrito errado uma vez e revertido — ver ADR-021.
- **ShedLock com `usingDbTime()`.** A referência de tempo é o relógio do banco:
  três máquinas com relógios diferentes calculando janelas diferentes anula o
  propósito de ter uma trava central.
- **Auto-renovação roda ANTES da expiração.** Invertido, o cliente recebe "seu
  contrato venceu" e, um minuto depois, "seu contrato foi renovado".
- **`ContractAutoRenewer` não passa pelo `ContractFinder`.** Job não tem
  carteira — é o sistema agindo, e forçar um usuário técnico só para satisfazer
  o filtro seria contornar a regra em vez de reconhecer que ela não se aplica.
- **Formatação de data e dinheiro acontece no Java, não no template.** O texto
  formatado fica gravado no payload JSONB, então "que aviso vocês me mandaram?"
  tem resposta exata mesmo que o template mude depois.
- **`th:text`, nunca `th:utext`.** É o escaping do Thymeleaf que impede um nome
  de cliente malicioso de virar script no cliente de e-mail de quem abrir.
- **`crbap.notification.smtp-enabled` é `false` por padrão.** Ligar isso sem
  querer com um dump de produção dispara aviso de verdade para cliente de
  verdade.
- **`ExpiringContractsPort` é declarada em `notification`** e implementada por
  `contract`: "quem eu preciso avisar hoje?" é pergunta de notificações.
- **`findByIdForUpdate` do billing e o `EntityGraph`** continuam separados — a
  mesma armadilha da fase 5 vale para qualquer agregado com coleção.

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

- **Publicar em `CaioCodes1/`** — o repositório já existe em `main` com dois
  commits, mas **sem remoto**. Enquanto não for publicado, continua na mesma
  situação do `bank-api`: existe só neste disco.
- Fase 7 (dashboard e auditoria) é a próxima. O `AuditLog` ainda não existe, e o
  painel vai precisar do cache no Redis que a nota 11 descreve.
- **A suíte de integração agora sobe três containers** (Postgres, Redis,
  RabbitMQ). Antes de rodar, subir o Docker com `D:\dev-tools\subir-docker.ps1`
  e conferir a folga de commit — ver a seção Ambiente abaixo.
- **`contract_items` não foi criada.** O DDL da nota 07 a prevê, mas o agregado
  não tem itens e tabela sem código é peso morto. Entra quando houver caso de uso.
- **`GET /clients/{id}/contracts` não existe** — use `GET /contracts?clientId=`.
- As listagens de contrato e de cobrança não trazem o nome do cliente (só o
  `clientId`), embora o join com `clients` já esteja na consulta por causa do
  escopo.
- **Juros e multa por atraso não existem.** `daysLate` é calculado, mas nada é
  acrescido ao valor — a régua de encargos não foi especificada.
- **Ambiente (02/09, parcialmente resolvido):** o Maven morria com
  `insufficient memory` e o Docker caía junto porque o limite de commit do
  Windows era ~17 GB (pagefile de 800 MB). Hoje a garantia é o **pagefile fixo
  de 4 GB no `C:`** (limite de 20.389 MB) — o do `D:`, apesar de configurado,
  **não é criado no boot**. Se voltar a acontecer, **medir o commit antes de
  culpar o Docker** — ver a seção Discos do `E:\projetos\CLAUDE.md`.
- **Docker Desktop 4.87 deixa socket órfão e não sobe (recorrente).** O backend
  morre com `remove ...sock: The file cannot be accessed by the system` em
  `%LOCALAPPDATA%\Docker\run` e `%LOCALAPPDATA%\docker-secrets-engine`. Os
  arquivos **não podem ser apagados** (`del` falha); o que funciona é
  **renomear o diretório inteiro** e recriá-lo vazio. Aconteceu 18 vezes entre
  23/08 e 03/09 — os diretórios renomeados estão acumulados no `AppData` e
  podem ser apagados.
- Falta gestão de usuários pela API (`POST /users`, conceder/revogar papéis).
  Hoje só existe leitura; usuário novo depende do `AdminBootstrap`.
- O limite de rate limit **por e-mail** (além do por IP) ainda não existe.
- `allowEmptyShould(true)` no `ArchitectureTest`: remover das regras cujo alvo
  já passou a existir (`@RestController` e `*UseCase` já existem desde a fase 2).
- Decidir entre o roadmap completo (12–14 semanas) e a versão reduzida de 4
  semanas descrita no fim da nota 18.
