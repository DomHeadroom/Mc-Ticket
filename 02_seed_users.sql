-- ============================================================
--  SEED UTENTI DI DEFAULT
--  Eseguito automaticamente da /docker-entrypoint-initdb.d/
--  Credenziali di sviluppo — NON usare in produzione
-- ============================================================

SET search_path TO helpdesk;

INSERT INTO users (email, full_name, password_hash, role, is_active) VALUES
    -- password: Admin1234!
    ('admin@mcticket.local',
     'Amministratore',
     '$2b$10$R6h5tw3em5KFPAqak5T3Te32zquxtygfLyWudr3GGl2TWXqh.J/uu',
     'admin',
     TRUE),

    -- password: User1234!
    ('user@mcticket.local',
     'Utente Demo',
     '$2b$10$P883cIrmR.44JJys/VMkXeHOduOQZLr10YTB7qpwI310gcktG601C',
     'requester',
     TRUE)

ON CONFLICT (email) DO NOTHING;
