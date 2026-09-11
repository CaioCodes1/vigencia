-- =====================================================================
-- V6 — trilha de auditoria (fase 7)
--
-- Tabela append-only: escreve, lê, e mais nada. A imutabilidade é
-- garantida no BANCO, em duas camadas independentes (gatilho e GRANT),
-- porque "ninguém vai fazer UPDATE nisso" não é garantia, é combinado.
-- =====================================================================

CREATE TABLE audit_logs (
    id             UUID PRIMARY KEY,
    entity_type    VARCHAR(60) NOT NULL,
    entity_id      UUID NOT NULL,
    action         VARCHAR(40) NOT NULL,
    -- SEM foreign key para users(id), de propósito — ver o bloco abaixo.
    actor_id       UUID,
    -- Desnormalizado: sobrevive à exclusão do usuário e é o que torna a
    -- linha legível três anos depois, quando o UUID não diz mais nada.
    actor_email    VARCHAR(255),
    ip_address     INET,
    user_agent     VARCHAR(500),
    trace_id       VARCHAR(64),
    before_data    JSONB,
    after_data     JSONB,
    changed_fields TEXT[],
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- POR QUE actor_id NÃO TEM FOREIGN KEY
--
-- O DDL da nota 07 previa REFERENCES users(id). Ele não sobrevive ao
-- gatilho de imutabilidade, e a incompatibilidade é real:
--
--   * ON DELETE RESTRICT  -> apagar um usuário passa a ser impossível
--     para sempre, porque a trilha dele nunca é apagada. Na prática, a
--     tabela de auditoria vira um bloqueio permanente do cadastro.
--   * ON DELETE SET NULL  -> o banco executa um UPDATE em audit_logs, e
--     o gatilho abaixo recusa. O DELETE do usuário falha com um erro que
--     fala de auditoria, e ninguém entende por quê.
--
-- Sem FK, o id fica como registro histórico e o actor_email responde
-- "quem foi?" mesmo depois de o usuário sumir. É a mesma escolha que
-- contracts.created_by faz com ON DELETE SET NULL, levada ao limite que
-- uma tabela imutável exige.
-- =====================================================================

-- "Quem mexeu neste contrato?" — a pergunta nº 1 de auditoria.
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id, created_at DESC);

-- "O que o Bruno fez ontem?" — a pergunta nº 2, a de investigação.
CREATE INDEX idx_audit_actor ON audit_logs (actor_id, created_at DESC);

-- BRIN, e não B-tree: a tabela só cresce no fim, em ordem de data. Um
-- B-tree aqui custaria gigabytes; o BRIN guarda a faixa de valores por
-- bloco e faz o mesmo trabalho de filtro por período com kilobytes.
CREATE INDEX idx_audit_created ON audit_logs USING brin (created_at);

-- jsonb_path_ops: metade do tamanho do GIN padrão e mais rápido, ao custo
-- de só atender o operador @> — que é exatamente o que "achar a linha em
-- que o campo X virou Y" precisa.
CREATE INDEX idx_audit_after_gin ON audit_logs USING gin (after_data jsonb_path_ops);

-- =====================================================================
-- CAMADA 1 — gatilho: vale para TODO MUNDO, inclusive superusuário.
--
-- É a camada que sobrevive ao ambiente: nos testes com Testcontainers a
-- aplicação conecta como superusuário, e superusuário ignora GRANT. Um
-- teste de "auditoria é imutável" apoiado só no REVOKE passaria verde sem
-- testar nada.
--
-- TRUNCATE não dispara gatilho de linha, e isso é intencional: expurgo
-- por retenção e limpeza entre testes precisam de uma saída. Em produção
-- quem fecha essa porta é o REVOKE da camada 2.
-- =====================================================================
CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs é append-only: % não é permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

-- =====================================================================
-- CAMADA 2 — privilégio: a aplicação FISICAMENTE não consegue alterar.
--
-- Regra de negócio virada em permissão de sistema: nem um bug nosso, nem
-- um SQL injection que escape de tudo, apaga a trilha.
--
-- Condicional porque o papel só existe onde alguém o criou (produção).
-- Em dev e nos testes a conexão é a dona da tabela, e aí quem protege é
-- o gatilho acima.
-- =====================================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vigencia_app') THEN
        EXECUTE 'REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM vigencia_app';
    END IF;
END $$;
