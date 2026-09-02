-- =====================================================================
-- V2 — clientes e contatos
-- Mesma convenção de tipos da V1: VARCHAR(n), e unicidade
-- case-insensitive por índice sobre lower().
-- =====================================================================

CREATE TABLE clients (
    id                 UUID PRIMARY KEY,
    legal_name         VARCHAR(200) NOT NULL,
    trade_name         VARCHAR(200),
    -- Documento cifrado em repouso (AES-256-GCM, IV por registro) e um
    -- índice cego (HMAC-SHA256 com chave secreta) para busca e unicidade.
    -- Hash puro não serve: o espaço de CPFs é ~10^11, quebrável em minutos.
    document_enc       BYTEA NOT NULL,
    document_index     BYTEA NOT NULL,
    document_type      VARCHAR(4)   NOT NULL,
    email              VARCHAR(255) NOT NULL,
    phone              VARCHAR(30),
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    account_manager_id UUID REFERENCES users(id) ON DELETE SET NULL,

    address_street     VARCHAR(200),
    address_number     VARCHAR(20),
    address_complement VARCHAR(100),
    address_district   VARCHAR(100),
    address_city       VARCHAR(100),
    address_state      CHAR(2),
    address_zip        VARCHAR(9),
    address_country    CHAR(2) NOT NULL DEFAULT 'BR',

    notes              VARCHAR(2000),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at     TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT ck_clients_status  CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT ck_clients_doctype CHECK (document_type IN ('CPF', 'CNPJ')),
    -- Estado e data andam juntos: INACTIVE sem data (ou o contrário) é
    -- dado corrompido, e o banco recusa.
    CONSTRAINT ck_clients_deact   CHECK ((status = 'INACTIVE') = (deactivated_at IS NOT NULL))
);

-- Documento único APENAS entre os ativos: um cliente desativado pode ser
-- recadastrado depois. Sem o índice, essa regra viveria só num SELECT antes
-- do INSERT — e duas requisições simultâneas passariam pelas duas leituras
-- antes de qualquer escrita.
CREATE UNIQUE INDEX uk_clients_document_active
    ON clients (document_index)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_clients_manager ON clients (account_manager_id)
    WHERE status = 'ACTIVE';

-- LIKE com % na frente NÃO usa B-tree. O trigram resolve a busca parcial,
-- e o f_unaccent (V1) faz "Jose" encontrar "José".
CREATE INDEX idx_clients_name_trgm
    ON clients USING gin (f_unaccent(lower(legal_name)) gin_trgm_ops);

CREATE INDEX idx_clients_email ON clients (lower(email));

CREATE TABLE client_contacts (
    id         UUID PRIMARY KEY,
    client_id  UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    name       VARCHAR(150) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    phone      VARCHAR(30),
    role       VARCHAR(60),
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contacts_client ON client_contacts (client_id);

-- No máximo um contato principal por cliente — garantido pelo banco,
-- não por um if no service.
CREATE UNIQUE INDEX uk_contact_primary
    ON client_contacts (client_id)
    WHERE is_primary;
