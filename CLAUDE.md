# Vigência

Plataforma de renovação de contratos e automação de cobrança. **Segundo projeto
Java do workspace** (o primeiro é o `billing-platform`).

Java 21 · Spring Boot 3.5 · PostgreSQL 16 · Flyway · MapStruct · Redis ·
RabbitMQ · Prometheus · Grafana · Loki · Testcontainers.

> **O nome mudou em 11/09/2026.** Chamava-se `contract-renewal-platform`, com o
> acrônimo `crbap` por dentro. Se `crbap` aparecer em algum lugar, é resquício —
> e resquício aqui é perigoso, porque o nome vive em cinco camadas que só
> funcionam se baterem entre si: o pacote Java, o prefixo das properties, os
> nomes de métrica (Java + alertas + dashboards), as filas do RabbitMQ e o
> issuer do JWT. Só a primeira falha de forma barulhenta; as outras falham em
> silêncio.

> **Estado: as 8 fases concluídas (01/09–10/09/2026).** 352 testes verdes
> (224 unitários + 128 de integração), 84% de cobertura de linha, 0 violações de
> Checkstyle.
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
> - **Fase 7** — auditoria e painel: trilha append-only garantida por gatilho e
>   por `REVOKE`, preenchida por AOP (`@Auditable`), com o "antes" entregue pelo
>   caso de uso e `changedFields` calculado; IP confiável só atrás de proxy
>   declarado; e o dashboard com cache no Redis **com o escopo dentro da
>   chave** — um serializador tipado por cache, sem *default typing*.
> - **Fase 8** — observabilidade e CI/CD: métricas de negócio atrás de uma porta
>   (gauge lendo memória, não o banco a cada scrape), log JSON para o Loki,
>   health da outbox no readiness, 4 dashboards do Grafana provisionados por
>   arquivo, alertas, `ci.yml` e `cd.yml`, smoke tests e o portão de cobertura
>   em 80% finalmente ligado.
>
> **O roadmap acabou.** O que vier agora é melhoria, não fase — ver Pendências.

## Rodar

Sem entrada no `.claude/launch.json` da raiz (API pura, sem front).

```bash
docker compose up -d      # infra: Postgres 5434, Redis 6380, RabbitMQ 5673
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test                  # unitários, sem Docker
mvn verify                # inclui os *IT (Testcontainers) e o portao do JaCoCo

docker compose --profile observability up -d   # Prometheus 9090, Grafana 3001
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

| | billing-platform | vigencia |
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

Todas justificadas num cofre do Obsidian mantido fora do repositório: 23 notas
de arquitetura, modelagem, API, segurança, testes e DevOps, com **27 ADRs** que
registram contexto, alternativas descartadas e reversibilidade. As que mais
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
- **`vigencia.jobs.enabled=false` no perfil de teste.** Com o agendador ligado, um
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
- **`vigencia.notification.smtp-enabled` é `false` por padrão.** Ligar isso sem
  querer com um dump de produção dispara aviso de verdade para cliente de
  verdade.
- **`ExpiringContractsPort` é declarada em `notification`** e implementada por
  `contract`: "quem eu preciso avisar hoje?" é pergunta de notificações.
- **`findByIdForUpdate` do billing e o `EntityGraph`** continuam separados — a
  mesma armadilha da fase 5 vale para qualquer agregado com coleção.


### Fase 7 — auditoria e dashboard

- **A trilha é imutável no banco, em duas camadas.** Um gatilho
  `BEFORE UPDATE OR DELETE` que levanta exceção (vale até para superusuário — é
  o que os testes conseguem verificar) e um `REVOKE UPDATE, DELETE, TRUNCATE`
  condicional para o usuário `vigencia_app` (vale em produção, onde a aplicação não
  é dona da tabela). Uma só das duas não cobre os dois ambientes.
- **`audit_logs.actor_id` NÃO tem foreign key**, ao contrário do DDL da nota 07.
  `ON DELETE RESTRICT` tornaria impossível excluir um usuário para sempre;
  `ON DELETE SET NULL` executaria um `UPDATE` que o gatilho recusa — e o erro
  falaria de auditoria no meio de um cadastro. O `actor_email` desnormalizado é
  o que responde "quem foi" depois da exclusão.
- **O `TRUNCATE` fica de fora do gatilho de propósito.** É a porta para expurgo
  por retenção e para a limpeza entre testes (`AbstractIamIntegrationTest` usa
  `TRUNCATE audit_logs`; `DELETE` ali quebra). Em produção quem fecha essa porta
  é o `REVOKE`.
- **O `AuditAspect` roda POR FORA da transação** (`@Order(LOWEST_PRECEDENCE - 1)`,
  uma casa acima do interceptador transacional). `proceed()` só volta depois do
  commit, então a trilha registra o que **aconteceu**, não o que se tentou. Por
  dentro, uma ação que desse rollback deixaria a linha dizendo que o contrato
  foi renovado. Há um teste para isso.
- **Falha ao auditar não derruba a ação.** A ação já commitou; jogar exceção
  devolveria erro por uma renovação que aconteceu, e o operador tentaria de
  novo. Fica `log.error` — que na fase 8 vira alerta.
- **O "antes" vem do caso de uso (`AuditContext`), não do aspecto.** Buscar o
  estado anterior genericamente exigiria um mapeador entidade→repositório dentro
  do aspecto. Guardar o **mesmo tipo que o método devolve** é o que faz o
  `changedFields` comparar maçã com maçã: os dois lados passam pelo mesmo
  `ObjectMapper`.
- **`document`, `password` e afins são redigidos antes de gravar.** Auditoria é
  lida por muito mais gente do que a tabela de clientes — é o caminho mais fácil
  para vazar o que a cifra de campo da fase 3 protege.
- **`server.forward-headers-strategy` virou `none`.** Com `framework`, o Spring
  reescreve `getRemoteAddr()` a partir do `X-Forwarded-For`, e sem proxy real na
  frente qualquer um escolhe o IP que a auditoria grava. O `ClientIpResolver` só
  olha o cabeçalho quando o *peer* está em `vigencia.audit.trusted-proxies`, e lê a
  lista **da direita para a esquerda** — o começo dela é o que o cliente
  escreveu.
- **Toda chave de cache do painel carrega o escopo** (`DashboardScope.cacheKey()`).
  `key = "'summary'"` faria o vendedor receber o painel do gestor — vazamento de
  autorização que não quebra tela nenhuma. É o teste que não pode faltar, e ele
  chama o endpoint **duas vezes** de propósito: com uma chamada só, o caminho do
  cache nem é exercitado.
- **`DashboardCache` é classe separada do caso de uso.** `@Cacheable` é atendido
  pelo proxy; o caso de uso resolvendo o escopo e chamando o próprio método
  anotado seria autoinvocação, e o cache simplesmente não existiria — sem erro
  nenhum. Mesma armadilha do `REQUIRES_NEW` da fase 6.
- **Um serializador tipado por cache, sem `GenericJackson2JsonRedisSerializer`.**
  *Default typing* grava o nome da classe no JSON e o usa para instanciar na
  leitura: quem escrever no Redis escolhe o que a aplicação instancia. De
  quebra, evita a dor de `List.of()` voltando como `ImmutableCollections$ListN`.
- **`CacheErrorHandler` que engole erro de leitura e gravação, mas não de
  `evict`/`clear`.** Redis fora do ar deixa o sistema lento, não errado — mas
  uma limpeza que falhou em silêncio serve dado velho como novo.
- **O aquecimento usa `@CachePut`, não `@Cacheable`.** O segundo devolveria o
  valor que ainda está lá e não recalcularia nada.
- **`(:scope IS NULL OR ...)` é aceitável no `reporting` e não no `audit`.** No
  painel toda consulta é agregação que varre o conjunto de qualquer forma; na
  auditoria o filtro precisa do índice para achar poucas linhas em milhões, e
  por isso lá o `WHERE` é montado dinamicamente.
- **O Redis não é limpo pelo `DELETE` das tabelas.** `AbstractIamIntegrationTest`
  limpa os caches do Spring antes de cada teste — sem isso, o painel calculado
  por um teste é servido para o seguinte, e a falha aparece num teste que não
  tem nada a ver com cache. Aconteceu nesta fase.
- **MRR é aproximação assumida**: o contrato guarda o total do período, não a
  mensalidade, então o valor é distribuído pelos dias e normalizado em 30. E os
  totais **somam sem separar moeda** — hoje toda a base é BRL; no dia em que não
  for, a correção é `GROUP BY currency`, não um fator de conversão.

### Fase 8 — observabilidade e CI/CD

- **O gauge lê memória, não o banco.** `Gauge.builder(nome, repo, Repo::count)`
  — a forma que aparece em toda documentação — roda a consulta a **cada scrape**,
  de 15 em 15 segundos, para sempre. Uma tarefa de um minuto atualiza
  `AtomicLong`s e o gauge lê memória. Alvo de monitoramento que pesa no sistema
  acaba desligado no primeiro incidente, que é quando ele importa. ADR-026.
- **`BusinessGauges` NÃO tem `@SchedulerLock`** (terceira vez que a distinção
  aparece): cada instância publica os próprios números e o Prometheus raspa
  todas. Com a trava, duas de três reportariam zero para sempre.
- **`/actuator/prometheus` é liberado por rede OU por `system:monitor`.** Um
  coletor não renova access token de 15 minutos, e `permitAll` serviria a
  contagem de contratos e o valor em atraso para a internet. ADR-027.
- **Isso depende de `forward-headers-strategy: none` (fase 7).** O endereço
  avaliado é o da conexão TCP. Em `framework`, o Spring o reescreve a partir do
  `X-Forwarded-For` e qualquer um se declara `10.0.0.1`. **As duas mudam juntas.**
- **O health da outbox conta só o que está parado há mais de 5 minutos.** Ativar
  um contrato anual emite doze eventos de uma vez; a contagem crua derrubaria o
  readiness num sistema saudável. Alarme que dispara sozinho é alarme ignorado.
- **A outbox entra no `readiness`, nunca no `liveness`.** Com ela entupida a
  aplicação está viva — no liveness, o orquestrador reiniciaria em laço infinito
  algo que não tem defeito.
- **O arquivo precisa se chamar `logback-spring.xml`.** Como `logback.xml`, o
  Logback carrega antes do Spring e os blocos `<springProfile>` são ignorados
  **em silêncio**.
- **Nenhuma métrica leva id como rótulo**, e há um teste que verifica isso.
  10.000 clientes × 5 status = 50.000 séries temporais. Id vai no log.
- **O Promtail descobre pelo daemon do Docker**, não por caminho de arquivo:
  aplicação em container que escreve o próprio log é log que some com o
  container. Só `level` e `container` viram label.
- **Spotless ficou de fora.** Ligá-lo reformataria mais de cem arquivos num
  commit que não tem nada a ver com formatação, e o `git blame` dos arquivos mais
  lidos iria junto. O Checkstyle já é o portão.
- **`jacoco.check.skip` virou `false`.** Ficou ligado em `true` desde a fase 1 de
  propósito: meta de 80% sobre um esqueleto só ensina a escrever teste de getter.
  Hoje sobra folga (84%).
- **`-DskipUnitTests` não existe no Maven.** Os unitários rodam de novo dentro do
  `verify` — 20 segundos. O job separado no CI existe pelo feedback rápido, não
  para economizar essa rodada.
- **O `if` de um step não enxerga o `env` declarado no próprio step.** O
  `SONAR_TOKEN` fica no nível do job; declarado no step, a condição avalia string
  vazia sempre e o passo nunca roda.
- **Os jobs de deploy são guardados por `vars.STAGING_HOST != ''`.** Sem isso,
  todo push na `main` de um repositório sem servidor falharia, e o CD viraria um
  X vermelho permanente. O `build-image` não é guardado: funciona só com o
  `GITHUB_TOKEN`.
- **O smoke test não testa regra de negócio.** Isso o CI já fez 352 vezes com
  banco de verdade; repetir contra produção só cria dado de mentira na base do
  cliente. Ele verifica ambiente — e a checagem mais importante é "a rota
  protegida ainda devolve 401", porque um deploy com a segurança desligada
  responde 200 em tudo e passaria por saudável.
- **`wait-healthy.sh` espera o readiness, não o liveness.** O liveness responde
  OK antes de o Flyway migrar; smoke test contra ele falha de forma
  intermitente.
## Convenções

Documentação, comentários e mensagens de erro em português; código em inglês;
commits `tipo: descrição`.

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

- **Publicar em `CaioCodes1/vigencia`** — o repositório está versionado em
  `main`, mas **sem remoto**. Enquanto não for publicado, continua na mesma
  situação do `bank-api`: existe só neste disco.
- **O roadmap acabou.** O que vem agora não é fase, é melhoria — e a primeira
  delas é publicar. Nada aqui bloqueia nada.
- **Alertmanager não existe.** As regras estão escritas e o Prometheus as
  avalia, mas não há para onde mandar: alerta sem destinatário é gráfico
  vermelho que ninguém vê. Entra com o primeiro canal real.
- **Sem exporters de Postgres, Redis e RabbitMQ.** O alerta `DlqComMensagens`
  depende do `rabbitmq_exporter` e fica inerte até ele existir.
- **Os 4 dashboards do Grafana nunca foram abertos.** O JSON é válido e o
  provisionamento está no lugar, mas a pilha não subiu nesta máquina — as
  consultas PromQL foram escritas, não vistas na tela.
- **Retenção da auditoria não existe.** `audit_logs` só cresce. O expurgo por
  período (ou o particionamento por mês) é trabalho de quando a tabela doer —
  a porta está aberta, porque `TRUNCATE` e `DROP PARTITION` não passam pelo
  gatilho.
- **Login e logout não são auditados.** No login ainda não existe autor para o
  aspecto resolver, e o IAM já registra tentativa falha, bloqueio e rate limit
  em log. Vira linha de auditoria quando houver exigência de compliance.
- **`vigencia.audit.trusted-proxies` está vazio e `forward-headers-strategy` é
  `none`.** Correto para rodar sem proxy; ao publicar atrás de um balanceador,
  os dois precisam ser preenchidos juntos — só um deles deixa o IP errado. E
  atenção: a liberação do `/actuator/prometheus` por rede depende do mesmo
  `getRemoteAddr()`, então mexer em um afeta os dois.
- **Spotless e `.editorconfig` ficaram de fora.** Entram quando houver uma
  segunda pessoa no projeto — reformatar cem arquivos hoje só destruiria o
  `git blame` dos arquivos mais lidos.
- **A suíte de integração agora sobe três containers** (Postgres, Redis,
  RabbitMQ). O Docker precisa estar de pé antes de rodar `mvn verify` —
  a suíte não sobe container sozinha.
- **`contract_items` não foi criada.** O DDL da nota 07 a prevê, mas o agregado
  não tem itens e tabela sem código é peso morto. Entra quando houver caso de uso.
- **`GET /clients/{id}/contracts` não existe** — use `GET /contracts?clientId=`.
- As listagens de contrato e de cobrança não trazem o nome do cliente (só o
  `clientId`), embora o join com `clients` já esteja na consulta por causa do
  escopo.
- **Juros e multa por atraso não existem.** `daysLate` é calculado, mas nada é
  acrescido ao valor — a régua de encargos não foi especificada.
- O painel **soma sem separar moeda** e o **MRR é aproximado** (valor do período
  distribuído por dia). Vira `GROUP BY currency` quando existir contrato fora do
  BRL.
- Falta gestão de usuários pela API (`POST /users`, conceder/revogar papéis).
  Hoje só existe leitura; usuário novo depende do `AdminBootstrap`.
- O limite de rate limit **por e-mail** (além do por IP) ainda não existe.
- Decidir entre o roadmap completo (12–14 semanas) e a versão reduzida de 4
  semanas descrita no fim da nota 18.
