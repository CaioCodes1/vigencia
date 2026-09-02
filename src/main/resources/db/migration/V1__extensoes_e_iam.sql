-- =====================================================================
-- V1 — extensões e identidade/acesso (IAM)
-- Ver "09 — Segurança" e "07 — Banco de Dados" no cofre do Obsidian.
--
-- CONVENÇÃO DE TIPOS: VARCHAR(n) em vez de TEXT ou citext.
-- Motivo prático, descoberto ao subir a aplicação: com
-- `ddl-auto: validate`, o Hibernate compara o tipo da coluna com o que a
-- entidade declara e reprova o schema em tipos que ele não conhece
-- ("wrong column type encountered in column [email]: found [citext],
-- but expecting [varchar(255)]"). A unicidade case-insensitive que o
-- citext daria continua garantida — por um índice único sobre lower().
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- O unaccent() é STABLE, e índice exige IMMUTABLE. Este wrapper fixa o
-- dicionário e permite indexar unaccent(lower(nome)) — sem ele, o
-- CREATE INDEX de V2 falha com "functions in index expression must be
-- marked IMMUTABLE".
CREATE OR REPLACE FUNCTION f_unaccent(text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT public.unaccent('public.unaccent'::regdictionary, $1)
$$;

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id                    UUID PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(150) NOT NULL,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    failed_login_attempts SMALLINT    NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    password_changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    must_change_password  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    version               BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ck_users_email_fmt  CHECK (email ~* '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
    CONSTRAINT ck_users_attempts   CHECK (failed_login_attempts >= 0)
);

-- Unicidade case-insensitive sem a extensão citext: "Ana@x.com" e
-- "ana@x.com" são a mesma pessoa, e é o banco que garante isso — não um
-- SELECT antes do INSERT, que duas requisições simultâneas atravessam.
CREATE UNIQUE INDEX uk_users_email ON users (lower(email));

COMMENT ON COLUMN users.password_hash IS 'Argon2id (m=19456 KiB, t=2, p=1) — recomendação OWASP';
COMMENT ON COLUMN users.version IS 'Lock otimista do Hibernate';

-- ---------------------------------------------------------------------
-- roles e permissions
-- ---------------------------------------------------------------------
CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    name        VARCHAR(40)  NOT NULL,
    description VARCHAR(255),
    -- Papel de sistema não pode ser apagado pela API.
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_roles_name UNIQUE (name),
    CONSTRAINT ck_roles_name CHECK (name ~ '^[A-Z_]{3,40}$')
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    name        VARCHAR(60) NOT NULL,   -- 'contract:renew'
    resource    VARCHAR(30) NOT NULL,   -- 'contract'
    action      VARCHAR(30) NOT NULL,   -- 'renew'
    description VARCHAR(255),

    CONSTRAINT uk_permissions_name UNIQUE (name),
    CONSTRAINT ck_permissions_name CHECK (name ~ '^[a-z_]+:[a-z_]+$')
);

CREATE TABLE user_roles (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- RESTRICT de propósito: apagar um papel ainda concedido deve FALHAR,
    -- em vez de tirar acesso de várias pessoas em silêncio.
    role_id    UUID NOT NULL REFERENCES roles(id) ON DELETE RESTRICT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID REFERENCES users(id),

    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);
CREATE INDEX idx_role_permissions_permission ON role_permissions (permission_id);

-- ---------------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- SHA-256 em hexadecimal: 64 caracteres, tamanho fixo. O valor cru do
    -- token nunca é gravado — um dump da tabela não dá sessão a ninguém.
    token_hash VARCHAR(64) NOT NULL,
    -- Detecção de reuso: um refresh já usado revoga a família inteira.
    family_id  UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    user_agent VARCHAR(255),
    -- VARCHAR(45) cobre IPv6. O tipo INET do Postgres seria mais correto
    -- (valida o endereço e entende operadores de rede), mas o Hibernate só
    -- o aceita com mapeamento especial, e a mesma troca já tinha custado o
    -- citext acima. Fica registrado como possível melhoria.
    ip_address VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_refresh_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_user_active ON refresh_tokens (user_id)
    WHERE revoked_at IS NULL AND used_at IS NULL;
CREATE INDEX idx_refresh_family  ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_expires ON refresh_tokens (expires_at);
