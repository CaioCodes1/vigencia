-- =====================================================================
-- Seed de papéis e permissões (migração REPETÍVEL — roda de novo sempre
-- que este arquivo muda, e depois de todas as versionadas).
--
-- Tudo é ON CONFLICT DO NOTHING: rodar duas vezes não duplica nada, e
-- conceder uma permissão nova é acrescentar uma linha aqui.
--
-- O usuário administrador NÃO é criado aqui: a senha precisa passar pelo
-- Argon2, o que SQL não faz. Quem cria é o AdminBootstrap, na subida da
-- aplicação, a partir de ADMIN_INITIAL_PASSWORD.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Permissões
-- ---------------------------------------------------------------------
INSERT INTO permissions (id, name, resource, action, description)
SELECT gen_random_uuid(), p.name, split_part(p.name, ':', 1), split_part(p.name, ':', 2), p.description
FROM (VALUES
    ('client:create',          'Cadastrar cliente'),
    ('client:read',            'Consultar clientes (restrito à própria carteira)'),
    ('client:read_all',        'Ver clientes de todas as carteiras'),
    ('client:read_sensitive',  'Ver documento completo do cliente (sem máscara)'),
    ('client:update',          'Atualizar cliente'),
    ('client:delete',          'Desativar cliente'),

    ('contract:create',        'Criar contrato'),
    ('contract:read',          'Consultar contratos'),
    ('contract:read_all',      'Ver contratos de todas as carteiras'),
    ('contract:update',        'Editar contrato em rascunho'),
    ('contract:activate',      'Ativar contrato'),
    ('contract:renew',         'Renovar contrato'),
    ('contract:cancel',        'Cancelar contrato'),

    ('billing:create',         'Criar cobrança'),
    ('billing:read',           'Consultar cobranças'),
    ('billing:read_all',       'Ver cobranças de todas as carteiras'),
    ('billing:cancel',         'Cancelar cobrança'),

    ('payment:create',         'Registrar pagamento'),
    ('payment:refund',         'Estornar pagamento'),

    ('notification:read',      'Consultar log de notificações'),
    ('notification:send',      'Enviar ou reenviar notificação'),

    ('dashboard:read',         'Ver o painel (restrito à própria carteira)'),
    ('dashboard:read_all',     'Ver o painel completo da empresa'),

    ('audit:read',             'Consultar a trilha de auditoria'),

    ('user:create',            'Criar usuário'),
    ('user:read',              'Consultar usuários'),
    ('user:update',            'Atualizar usuário'),
    ('user:manage_roles',      'Conceder e revogar papéis'),

    ('role:read',              'Consultar papéis'),
    ('role:create',            'Criar papel'),
    ('role:update',            'Editar papel'),

    ('system:monitor',         'Acessar métricas e endpoints de operação'),
    ('system:docs',            'Acessar a documentação da API em produção')
) AS p(name, description)
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------
-- Papéis
-- ---------------------------------------------------------------------
INSERT INTO roles (id, name, description, system_role)
SELECT gen_random_uuid(), r.name, r.description, TRUE
FROM (VALUES
    ('ADMIN',       'Acesso total, incluindo gestão de usuários e auditoria'),
    ('MANAGER',     'Gestor comercial: contratos, clientes e painel completo'),
    ('FINANCE',     'Financeiro: cobranças, pagamentos e estornos'),
    ('SALES',       'Vendedor: cadastra cliente e contrato, vê só a própria carteira'),
    ('INTEGRATION', 'Conta de sistema (ERP): leitura e baixa de pagamento'),
    ('AUDITOR',     'Somente leitura, incluindo a trilha de auditoria')
) AS r(name, description)
ON CONFLICT (name) DO NOTHING;

-- ---------------------------------------------------------------------
-- ADMIN recebe tudo. É o único papel definido por "todas" em vez de lista:
-- permissão nova nasce concedida ao ADMIN e negada a todo o resto, que é o
-- padrão seguro (deny by default).
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------
-- Demais papéis: a matriz de "09 — Segurança", linha a linha.
-- ---------------------------------------------------------------------
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (VALUES
    -- MANAGER — gestor comercial
    ('MANAGER', 'client:create'),      ('MANAGER', 'client:read'),
    ('MANAGER', 'client:read_all'),
    ('MANAGER', 'client:update'),      ('MANAGER', 'client:delete'),
    ('MANAGER', 'contract:create'),    ('MANAGER', 'contract:read'),
    ('MANAGER', 'contract:read_all'),
    ('MANAGER', 'contract:update'),    ('MANAGER', 'contract:activate'),
    ('MANAGER', 'contract:renew'),     ('MANAGER', 'contract:cancel'),
    ('MANAGER', 'billing:create'),     ('MANAGER', 'billing:read'),
    ('MANAGER', 'billing:read_all'),
    ('MANAGER', 'notification:read'),  ('MANAGER', 'notification:send'),
    ('MANAGER', 'dashboard:read'),     ('MANAGER', 'dashboard:read_all'),

    -- FINANCE — dinheiro entra e sai por aqui, mas não mexe em contrato
    ('FINANCE', 'client:read'),        ('FINANCE', 'client:read_all'),
    ('FINANCE', 'client:read_sensitive'),
    ('FINANCE', 'contract:read'),      ('FINANCE', 'contract:read_all'),
    ('FINANCE', 'billing:create'),     ('FINANCE', 'billing:read'),
    ('FINANCE', 'billing:read_all'),
    ('FINANCE', 'billing:cancel'),
    ('FINANCE', 'payment:create'),     ('FINANCE', 'payment:refund'),
    ('FINANCE', 'notification:read'),
    ('FINANCE', 'dashboard:read'),     ('FINANCE', 'dashboard:read_all'),

    -- SALES — sem dashboard:read_all de propósito: vê só a própria carteira
    ('SALES', 'client:create'),        ('SALES', 'client:read'),
    ('SALES', 'client:update'),
    ('SALES', 'contract:create'),      ('SALES', 'contract:read'),
    ('SALES', 'billing:read'),
    ('SALES', 'dashboard:read'),

    -- INTEGRATION — conta de sistema, escrita só em pagamento
    ('INTEGRATION', 'client:read'),    ('INTEGRATION', 'client:read_all'),
    ('INTEGRATION', 'contract:read'), ('INTEGRATION', 'contract:read_all'),
    ('INTEGRATION', 'billing:read'), ('INTEGRATION', 'billing:read_all'),
    ('INTEGRATION', 'payment:create'),

    -- AUDITOR — leitura de tudo, escrita de nada
    ('AUDITOR', 'client:read'),        ('AUDITOR', 'client:read_all'),
    ('AUDITOR', 'client:read_sensitive'),
    ('AUDITOR', 'contract:read'),      ('AUDITOR', 'contract:read_all'),
    ('AUDITOR', 'billing:read'),       ('AUDITOR', 'billing:read_all'),
    ('AUDITOR', 'notification:read'),
    ('AUDITOR', 'dashboard:read'),     ('AUDITOR', 'dashboard:read_all'),
    ('AUDITOR', 'audit:read'),
    ('AUDITOR', 'user:read'),          ('AUDITOR', 'role:read')
) AS m(role_name, perm_name)
JOIN roles r       ON r.name = m.role_name
JOIN permissions p ON p.name = m.perm_name
ON CONFLICT DO NOTHING;
