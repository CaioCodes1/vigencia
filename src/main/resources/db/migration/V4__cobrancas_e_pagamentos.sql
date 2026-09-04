-- =====================================================================
-- V4 — cobranças e pagamentos (fase 5)
--
-- ON DELETE RESTRICT em tudo que é dinheiro. Cobrança e pagamento nunca
-- são apagados em cascata: se apagar um cliente pudesse apagar o
-- histórico financeiro, a contabilidade da empresa dependeria de um
-- DELETE bem-comportado.
-- =====================================================================

CREATE TABLE billings (
    id                 UUID PRIMARY KEY,
    -- Nulo = cobrança avulsa (multa, serviço extra), sem contrato por trás.
    contract_id        UUID REFERENCES contracts(id) ON DELETE RESTRICT,
    client_id          UUID NOT NULL REFERENCES clients(id) ON DELETE RESTRICT,
    reference          VARCHAR(80) NOT NULL,
    installment        SMALLINT,
    total_installments SMALLINT,
    amount             NUMERIC(15,2) NOT NULL,
    currency           VARCHAR(3) NOT NULL DEFAULT 'BRL',
    due_date           DATE NOT NULL,
    issue_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key    VARCHAR(100),
    notes              VARCHAR(2000),
    cancellation_reason VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_billings_amount CHECK (amount > 0),
    CONSTRAINT ck_billings_status CHECK (status IN
        ('PENDING','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED')),
    CONSTRAINT ck_billings_installment CHECK (
        installment IS NULL OR installment BETWEEN 1 AND total_installments),
    -- Parcela só existe dentro de contrato: cobrança avulsa não é "1 de 12".
    CONSTRAINT ck_billings_avulsa CHECK (contract_id IS NOT NULL OR installment IS NULL),
    CONSTRAINT ck_billings_cancel CHECK (
        (status = 'CANCELLED') = (cancellation_reason IS NOT NULL))
);

CREATE UNIQUE INDEX uk_billings_idem ON billings (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Não gerar duas vezes a mesma parcela do mesmo contrato. É a garantia que
-- sobrevive a uma ativação repetida por retry de rede — o SELECT antes do
-- INSERT não sobrevive.
CREATE UNIQUE INDEX uk_billings_contract_installment
    ON billings (contract_id, installment)
    WHERE contract_id IS NOT NULL AND status <> 'CANCELLED';

-- O job de inadimplência: só olha o que ainda pode vencer.
CREATE INDEX idx_billings_due_status ON billings (due_date, status)
    WHERE status IN ('PENDING','PARTIALLY_PAID');
CREATE INDEX idx_billings_client_status ON billings (client_id, status);
CREATE INDEX idx_billings_contract ON billings (contract_id);
CREATE INDEX idx_billings_overdue ON billings (client_id, due_date)
    WHERE status = 'OVERDUE';

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    billing_id      UUID NOT NULL REFERENCES billings(id) ON DELETE RESTRICT,
    amount          NUMERIC(15,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'BRL',
    method          VARCHAR(20) NOT NULL,
    paid_at         TIMESTAMPTZ NOT NULL,
    external_id     VARCHAR(100),
    idempotency_key VARCHAR(100) NOT NULL,
    -- Estorno NÃO apaga a linha: marca e recalcula o status da cobrança.
    -- Pagamento apagado é dinheiro que entrou e sumiu do histórico.
    refunded        BOOLEAN NOT NULL DEFAULT FALSE,
    refunded_at     TIMESTAMPTZ,
    refund_reason   VARCHAR(500),
    registered_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN
        ('PIX','BOLETO','CREDIT_CARD','DEBIT_CARD','BANK_TRANSFER','CASH','OTHER')),
    CONSTRAINT ck_payments_refund CHECK (refunded = (refunded_at IS NOT NULL)),
    CONSTRAINT ck_payments_refund_reason CHECK (
        refunded = (refund_reason IS NOT NULL))
);

-- Idempotência do pagamento: repetir a mesma chave devolve o mesmo pagamento
-- em vez de cobrar duas vezes. Aqui NÃO é parcial — todo pagamento tem chave.
CREATE UNIQUE INDEX uk_payments_idem ON payments (idempotency_key);
CREATE INDEX idx_payments_billing ON payments (billing_id) WHERE NOT refunded;
CREATE INDEX idx_payments_paid_at ON payments (paid_at DESC);
