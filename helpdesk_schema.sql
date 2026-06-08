-- ============================================================
--  HELPDESK TICKET SYSTEM — PostgreSQL DDL
--  Schema: helpdesk
--  Versione: 1.1.0
-- ============================================================

CREATE SCHEMA IF NOT EXISTS helpdesk;
SET search_path TO helpdesk;

-- ============================================================
-- EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";        -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";         -- Full-text / keyword search
CREATE EXTENSION IF NOT EXISTS "btree_gin";       -- GIN on composite indexes

-- ============================================================
-- ENUMERATIONS
-- ============================================================

CREATE TYPE ticket_status AS ENUM (
    'open',
    'in_progress',
    'pending_user',
    'resolved',
    'closed',
    'rejected'
);

CREATE TYPE urgency_level AS ENUM (
    'low',
    'medium',
    'high',
    'critical'
);

CREATE TYPE priority_level AS ENUM (
    'p1',   -- critico
    'p2',   -- alto
    'p3',   -- medio
    'p4'    -- basso
);

CREATE TYPE import_status AS ENUM (
    'queued',
    'processing',
    'completed',
    'failed'
);

CREATE TYPE attachment_source AS ENUM (
    'user_upload',
    'bulk_import'
);

-- ============================================================
-- TABELLA: users
-- Utenti del sistema (chi apre i ticket e chi li gestisce)
-- ============================================================
CREATE TABLE users (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255)    NOT NULL,
    full_name       VARCHAR(255)    NOT NULL,
    -- [v1.1] Login minimale: email + password_hash (bcrypt)
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(50)     NOT NULL DEFAULT 'requester'
                        CHECK (role IN ('requester', 'agent', 'admin')),
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email    UNIQUE (email)
);

-- ============================================================
-- TABELLA: categories
-- Categorie di classificazione dei ticket
-- Supporta gerarchia (parent_id) per sotto-categorie
-- ============================================================
CREATE TABLE categories (
    id              SERIAL          PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    slug            VARCHAR(100)    NOT NULL,
    description     TEXT,
    parent_id       INTEGER         REFERENCES categories(id) ON DELETE SET NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_categories_slug UNIQUE (slug)
);

-- ============================================================
-- TABELLA: tickets
-- Entità centrale del sistema
-- ============================================================
CREATE TABLE tickets (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    title                   VARCHAR(500)    NOT NULL,
    description             TEXT            NOT NULL,

    -- Classificazione
    status                  VARCHAR(50)     NOT NULL DEFAULT 'open',
    urgency_reported        VARCHAR(50)     NOT NULL DEFAULT 'medium',
    priority_computed       VARCHAR(50),              -- valorizzata dal NLP engine
    category_id_user        INTEGER         REFERENCES categories(id) ON DELETE SET NULL,
    category_id_auto        INTEGER         REFERENCES categories(id) ON DELETE SET NULL,

    -- Relazioni
    requester_id            UUID            NOT NULL  REFERENCES users(id) ON DELETE RESTRICT,
    assigned_agent_id       UUID            REFERENCES users(id) ON DELETE SET NULL,

    -- Provenienza
    source                  VARCHAR(50)     NOT NULL DEFAULT 'manual'
                                CHECK (source IN ('manual', 'bulk_csv', 'bulk_json')),
    bulk_import_id          UUID,           -- FK definita dopo la creazione di bulk_imports

    -- Analisi NLP (risultati sintetici inline, dettaglio in ticket_nlp_analysis)
    nlp_processed           BOOLEAN         NOT NULL DEFAULT FALSE,
    nlp_processed_at        TIMESTAMPTZ,

    -- Timestamp
    opened_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at             TIMESTAMPTZ,
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_resolved_after_opened
        CHECK (resolved_at IS NULL OR resolved_at >= opened_at),
    CONSTRAINT chk_closed_after_opened
        CHECK (closed_at IS NULL OR closed_at >= opened_at)
);

-- ============================================================
-- TABELLA: ticket_nlp_analysis
-- Risultati dell'analisi NLP (1:1 con tickets)
-- Separata per single-responsibility e scalabilità
-- ============================================================
CREATE TABLE ticket_nlp_analysis (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id           UUID            NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    model_version       VARCHAR(100)    NOT NULL,
    raw_output          JSONB,                          -- risposta grezza del modello
    suggested_category_id INTEGER       REFERENCES categories(id) ON DELETE SET NULL,
    suggested_priority  VARCHAR(50),
    confidence_score    NUMERIC(5,4)    CHECK (confidence_score BETWEEN 0 AND 1),
    language_detected   VARCHAR(10),
    processed_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_nlp_per_ticket UNIQUE (ticket_id)
);

-- ============================================================
-- TABELLA: keywords
-- Dizionario normalizzato delle parole chiave
-- ============================================================
CREATE TABLE keywords (
    id          SERIAL          PRIMARY KEY,
    term        VARCHAR(200)    NOT NULL,
    frequency   INTEGER         NOT NULL DEFAULT 0 CHECK (frequency >= 0),

    CONSTRAINT uq_keywords_term UNIQUE (term)
);

-- ============================================================
-- TABELLA: ticket_keywords (N:N)
-- Associazione ticket ↔ keyword estratta dal NLP
-- ============================================================
CREATE TABLE ticket_keywords (
    ticket_id       UUID        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    keyword_id      INTEGER     NOT NULL REFERENCES keywords(id) ON DELETE CASCADE,
    relevance_score NUMERIC(5,4) CHECK (relevance_score BETWEEN 0 AND 1),
    extracted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (ticket_id, keyword_id)
);

-- ============================================================
-- TABELLA: attachments
-- File allegati ai ticket (txt, log, json, csv)
-- ============================================================
CREATE TABLE attachments (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id       UUID            NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    file_name       VARCHAR(500)    NOT NULL,
    file_size_bytes BIGINT          NOT NULL CHECK (file_size_bytes > 0),
    mime_type       VARCHAR(100)    NOT NULL,
    storage_path    TEXT            NOT NULL,           -- path oggetto su object storage (S3/GCS/etc.)
    source          VARCHAR(50) NOT NULL DEFAULT 'user_upload',
    uploaded_by     UUID            REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_allowed_mime
        CHECK (mime_type IN (
            'text/plain',
            'text/csv',
            'application/json',
            'application/octet-stream'   -- fallback per file .log
        ))
);

-- ============================================================
-- TABELLA: bulk_imports
-- Traccia le importazioni massive (CSV / JSON)
-- ============================================================
CREATE TABLE bulk_imports (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_by     UUID            NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    file_name       VARCHAR(500)    NOT NULL,
    file_format     VARCHAR(10)     NOT NULL CHECK (file_format IN ('csv', 'json')),
    total_rows      INTEGER         NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    processed_rows  INTEGER         NOT NULL DEFAULT 0 CHECK (processed_rows >= 0),
    failed_rows     INTEGER         NOT NULL DEFAULT 0 CHECK (failed_rows >= 0),
    status          VARCHAR(50)   NOT NULL DEFAULT 'queued',
    error_log       JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ
);

-- Aggiunta FK circolare dopo la creazione di entrambe le tabelle
ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_bulk_import
    FOREIGN KEY (bulk_import_id) REFERENCES bulk_imports(id) ON DELETE SET NULL;

-- ============================================================
-- TABELLA: ticket_status_history
-- Audit trail dei cambi di stato
-- ============================================================
CREATE TABLE ticket_status_history (
    id              BIGSERIAL       PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    from_status     VARCHAR(50),                     -- NULL se è lo stato iniziale
    to_status       VARCHAR(50)   NOT NULL,
    changed_by      UUID            REFERENCES users(id) ON DELETE SET NULL,
    changed_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    comment         TEXT
);

-- ============================================================
-- TABELLA: ticket_comments
-- Messaggi/note su un ticket
-- ============================================================
CREATE TABLE ticket_comments (
    id              BIGSERIAL       PRIMARY KEY,
    ticket_id       UUID            NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    author_id       UUID            REFERENCES users(id) ON DELETE SET NULL,
    body            TEXT            NOT NULL,
    is_internal     BOOLEAN         NOT NULL DEFAULT FALSE,   -- nota interna agenti
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- ============================================================
-- TRIGGER: set_updated_at
-- [v1.1] Aggiorna automaticamente updated_at prima di ogni UPDATE
-- Applicato a: users, tickets, ticket_comments
-- ============================================================

-- Funzione condivisa da tutti i trigger updated_at
CREATE OR REPLACE FUNCTION helpdesk.fn_set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

-- Trigger su users
CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION helpdesk.fn_set_updated_at();

-- Trigger su tickets
CREATE TRIGGER trg_tickets_set_updated_at
    BEFORE UPDATE ON tickets
    FOR EACH ROW
    EXECUTE FUNCTION helpdesk.fn_set_updated_at();

-- Trigger su ticket_comments
CREATE TRIGGER trg_ticket_comments_set_updated_at
    BEFORE UPDATE ON ticket_comments
    FOR EACH ROW
    EXECUTE FUNCTION helpdesk.fn_set_updated_at();

-- ============================================================
-- INDICI
-- ============================================================

-- tickets — ricerche per stato, priorità, categoria, data
CREATE INDEX idx_tickets_status           ON tickets (status);
CREATE INDEX idx_tickets_priority         ON tickets (priority_computed);
CREATE INDEX idx_tickets_urgency          ON tickets (urgency_reported);
CREATE INDEX idx_tickets_category_user    ON tickets (category_id_user);
CREATE INDEX idx_tickets_category_auto    ON tickets (category_id_auto);
CREATE INDEX idx_tickets_requester        ON tickets (requester_id);
CREATE INDEX idx_tickets_agent            ON tickets (assigned_agent_id);
CREATE INDEX idx_tickets_opened_at        ON tickets (opened_at DESC);
CREATE INDEX idx_tickets_nlp_processed    ON tickets (nlp_processed) WHERE nlp_processed = FALSE;
CREATE INDEX idx_tickets_bulk_import      ON tickets (bulk_import_id) WHERE bulk_import_id IS NOT NULL;

-- Full-text search su titolo e descrizione (GIN tsvector)
CREATE INDEX idx_tickets_fts ON tickets
    USING GIN (to_tsvector('italian', coalesce(title,'') || ' ' || coalesce(description,'')));

-- keyword trgm per ricerca fuzzy
CREATE INDEX idx_keywords_trgm ON keywords USING GIN (term gin_trgm_ops);

-- ticket_keywords — per ricerca inversa (keyword → tickets)
CREATE INDEX idx_ticket_keywords_kw ON ticket_keywords (keyword_id);

-- attachments
CREATE INDEX idx_attachments_ticket ON attachments (ticket_id);

-- history
CREATE INDEX idx_status_history_ticket ON ticket_status_history (ticket_id, changed_at DESC);

-- comments
CREATE INDEX idx_comments_ticket ON ticket_comments (ticket_id, created_at DESC);

-- [v1.1] Indice su ticket_comments(author_id) per ricerche per autore
CREATE INDEX idx_comments_author ON ticket_comments (author_id);

-- bulk_imports
CREATE INDEX idx_bulk_imports_status ON bulk_imports (status) WHERE status IN ('queued','processing');

-- ============================================================
-- SEED CATEGORIE DI DEFAULT
-- ============================================================
INSERT INTO categories (name, slug, description) VALUES
    ('Rete',             'rete',             'Problemi di connettività e configurazione di rete'),
    ('Database',         'database',         'Errori, performance o configurazione DB'),
    ('Bug applicativo',  'bug-applicativo',  'Malfunzionamenti del software applicativo'),
    ('Configurazione',   'configurazione',   'Setup, parametri, variabili d''ambiente'),
    ('Hardware',         'hardware',         'Guasti o malfunzionamenti hardware'),
    ('Servizi web',      'servizi-web',      'API, endpoint HTTP, certificati SSL'),
    ('Altro',            'altro',            'Categoria generica non classificata');
