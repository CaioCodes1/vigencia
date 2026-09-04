-- =====================================================================
-- V3 — contratos e outbox (fase 4)
--
-- Duas tabelas numa migração só de propósito: o contrato é o primeiro
-- agregado que emite evento, e evento sem outbox é evento perdido no
-- primeiro rollback. Nascem juntos porque só fazem sentido juntos.
-- =====================================================================

CREATE TABLE contracts (
    id                   UUID PRIMARY KEY,
    number               VARCHAR(40) NOT NULL,
    client_id            UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    title                VARCHAR(200) NOT NULL,
    description          VARCHAR(2000),
    start_date           DATE NOT NULL,
    end_date             DATE NOT NULL,
    value_amount         NUMERIC(15,2) NOT NULL,
    value_currency       VARCHAR(3) NOT NULL DEFAULT 'BRL',
    billing_cycle        VARCHAR(20) NOT NULL,
    billing_day          SMALLINT,
    grace_period_days    SMALLINT NOT NULL DEFAULT 0,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    auto_renew           BOOLEAN NOT NULL DEFAULT FALSE,
    previous_contract_id UUID REFERENCES contracts(id) ON DELETE SET NULL,
    cancellation_reason  VARCHAR(500),
    cancelled_at         TIMESTAMPTZ,
    activated_at         TIMESTAMPTZ,
    idempotency_key      VARCHAR(100),
    -- ON DELETE SET NULL como em clients.account_manager_id: apagar um usuário
    -- não pode ser impedido por um contrato de dois anos atrás, e o autor já
    -- está preservado na trilha de auditoria.
    created_by           UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    version              BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_contracts_number UNIQUE (number),
    CONSTRAINT ck_contracts_period CHECK (start_date < end_date),
    CONSTRAINT ck_contracts_value  CHECK (value_amount > 0),
    CONSTRAINT ck_contracts_status CHECK (status IN
        ('DRAFT','ACTIVE','SUSPENDED','RENEWED','EXPIRED','CANCELLED')),
    CONSTRAINT ck_contracts_cycle CHECK (billing_cycle IN
        ('MONTHLY','QUARTERLY','SEMIANNUAL','YEARLY','ONE_TIME')),
    -- Dia 29, 30 e 31 não existem em todo mês. Limitar a 28 elimina a classe
    -- inteira de bug "a cobrança de fevereiro não foi gerada".
    CONSTRAINT ck_contracts_billing_day CHECK (billing_day BETWEEN 1 AND 28),
    CONSTRAINT ck_contracts_grace CHECK (grace_period_days BETWEEN 0 AND 90),
    -- Motivo e cancelamento andam juntos nos dois sentidos: não existe
    -- contrato cancelado sem motivo, nem motivo em contrato vigente.
    CONSTRAINT ck_contracts_cancel CHECK (
        (status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
    CONSTRAINT ck_contracts_no_self_ref CHECK (id <> previous_contract_id)
);

-- O índice mais importante do sistema: é ele que o job diário usa para achar
-- o que vence hoje, em D-30, D-15, D-7 e D-1. Sem ele é seq scan na tabela
-- inteira, cinco vezes por dia, para sempre.
CREATE INDEX idx_contracts_expiring ON contracts (end_date)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_contracts_client_status ON contracts (client_id, status);
CREATE INDEX idx_contracts_status_end ON contracts (status, end_date DESC);
CREATE INDEX idx_contracts_previous ON contracts (previous_contract_id)
    WHERE previous_contract_id IS NOT NULL;
CREATE INDEX idx_contracts_autorenew ON contracts (end_date)
    WHERE status = 'ACTIVE' AND auto_renew;

-- Idempotência da renovação. É este índice — e não o SELECT do caso de uso —
-- que impede duas requisições simultâneas com a mesma chave de gerarem dois
-- sucessores. O parcial existe porque a esmagadora maioria dos contratos
-- nasce sem chave (criação normal), e NULL não conflita com NULL.
CREATE UNIQUE INDEX uk_contracts_idempotency ON contracts (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Um contrato só pode ter UM sucessor. Sem isto, duas renovações concorrentes
-- que escapassem do lock deixariam a cadeia bifurcada — e nenhuma consulta
-- posterior conseguiria dizer qual é o contrato vigente.
CREATE UNIQUE INDEX uk_contracts_one_successor ON contracts (previous_contract_id)
    WHERE previous_contract_id IS NOT NULL;

-- =====================================================================
-- Outbox
--
-- O evento é gravado na MESMA transação que muda o agregado. Publicar
-- direto no broker dentro do caso de uso criaria o pior dos mundos: ou o
-- e-mail sai e o banco faz rollback, ou o banco grava e o broker cai.
-- Com a outbox existe um só commit, e a publicação é um problema separado.
-- =====================================================================
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id   UUID NOT NULL,
    event_type     VARCHAR(80) NOT NULL,
    event_version  SMALLINT NOT NULL DEFAULT 1,
    payload        JSONB NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       SMALLINT NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice parcial: o relay só procura o que ainda não saiu. Depois de
-- publicado, a linha sai do índice sozinha — o índice não cresce com o
-- histórico, só com a fila.
CREATE INDEX idx_outbox_unpublished ON outbox_events (occurred_at)
    WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);

-- Sequência do número do contrato (CT-<ano>-<sequência>). É do banco, e não um
-- MAX(number)+1 na aplicação: duas requisições simultâneas leriam o mesmo MAX
-- e tentariam gravar o mesmo número. A sequência entrega valores distintos
-- mesmo fora de transação, que é exatamente a propriedade necessária aqui.
CREATE SEQUENCE contract_number_seq START WITH 1 INCREMENT BY 1;
