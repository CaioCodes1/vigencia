-- =====================================================================
-- V5 — notificações e trava distribuída dos jobs (fase 6)
-- =====================================================================

CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    type         VARCHAR(40) NOT NULL,
    channel      VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    contract_id  UUID REFERENCES contracts(id) ON DELETE CASCADE,
    billing_id   UUID REFERENCES billings(id) ON DELETE CASCADE,
    client_id    UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    -- 30, 15, 7, 1 antes; -1, -7, -15 depois. Nulo quando o aviso não tem
    -- janela (renovação, pagamento recebido).
    days_offset  SMALLINT,
    recipient    VARCHAR(255) NOT NULL,
    subject      VARCHAR(200),
    payload      JSONB,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts     SMALLINT NOT NULL DEFAULT 0,
    last_error   VARCHAR(1000),
    scheduled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_notif_status CHECK (status IN ('PENDING','SENT','FAILED','CANCELLED')),
    CONSTRAINT ck_notif_channel CHECK (channel IN ('EMAIL','SMS','WEBHOOK','IN_APP')),
    CONSTRAINT ck_notif_target CHECK (contract_id IS NOT NULL OR billing_id IS NOT NULL),
    CONSTRAINT ck_notif_sent CHECK ((status = 'SENT') = (sent_at IS NOT NULL))
);

-- =====================================================================
-- A GARANTIA DE NÃO DUPLICAR: um aviso por contrato, por tipo, por janela,
-- por destinatário.
--
-- É a resposta definitiva ao "e se o job rodar duas vezes?". Rodou duas vezes
-- (cron repetido, restart no meio, relógio errado, alguém disparando na mão) →
-- o segundo INSERT estoura violação de unicidade → o código captura e ignora.
-- A garantia está no banco, não na esperança de o cron se comportar.
--
-- O 'recipient' entra na chave porque o mesmo aviso vai para o contato do
-- cliente E para o gestor da conta: são duas linhas legítimas da mesma janela.
--
-- O WHERE status <> 'FAILED' deixa o reenvio manual (RF-25) funcionar: um aviso
-- que falhou definitivamente pode ser recriado.
-- =====================================================================
CREATE UNIQUE INDEX uk_notif_contract_window
    ON notifications (contract_id, type, days_offset, recipient)
    WHERE contract_id IS NOT NULL AND status <> 'FAILED';

-- Para cobranca a chave exclui PAYMENT_RECEIVED: um recibo por pagamento e
-- legitimo, e a mesma cobranca pode receber varios pagamentos parciais. Ja
-- "sua cobranca venceu ha 7 dias" nao pode sair duas vezes.
CREATE UNIQUE INDEX uk_notif_billing_window
    ON notifications (billing_id, type, days_offset, recipient)
    WHERE billing_id IS NOT NULL AND status <> 'FAILED'
      AND type IN ('BILLING_CREATED', 'BILLING_DUE_SOON', 'BILLING_OVERDUE');

-- =====================================================================
-- Deduplicacao dos avisos vindos de EVENTO.
--
-- A outbox entrega ao menos uma vez: se o processo morrer entre publicar e
-- commitar, o mesmo evento sai de novo. As janelas do scheduler ja estao
-- cobertas pelos indices acima, mas um recibo de pagamento nao tem janela --
-- e sem isto uma redelivery mandaria dois recibos do mesmo PIX.
--
-- O consumidor grava o eventId no payload; o indice faz o resto. E por isso
-- que consumidor idempotente, neste projeto, nao depende de o consumidor
-- lembrar de ser idempotente.
-- =====================================================================
CREATE UNIQUE INDEX uk_notif_event
    ON notifications ((payload ->> 'eventId'), type, recipient)
    WHERE payload ->> 'eventId' IS NOT NULL AND status <> 'FAILED';

-- O job de envio: só procura o que ainda não saiu.
CREATE INDEX idx_notif_pending ON notifications (scheduled_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_notif_client ON notifications (client_id, created_at DESC);
CREATE INDEX idx_notif_contract ON notifications (contract_id)
    WHERE contract_id IS NOT NULL;

-- =====================================================================
-- ShedLock — trava distribuída dos jobs agendados.
--
-- Com 3 instâncias no ar, @Scheduled dispara nas três às 3h e o cliente recebe
-- 3 e-mails. A primeira instância a inserir a linha ganha; as outras pulam.
-- O schema das colunas é definido pela biblioteca — não inventar nomes.
-- =====================================================================
CREATE TABLE shedlock (
    name       VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at  TIMESTAMPTZ NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
