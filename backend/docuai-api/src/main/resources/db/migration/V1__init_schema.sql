-- =============================================================================
-- DocuAI — schéma initial
-- Section 5 du cahier des charges + ajouts requis (RAG, jobs d'extraction).
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- -----------------------------------------------------------------------------
-- Identité et RBAC
-- -----------------------------------------------------------------------------
CREATE TABLE utilisateur (
    id_utilisateur     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe_hash  VARCHAR(255) NOT NULL,
    prenom             VARCHAR(100) NOT NULL,
    nom                VARCHAR(100) NOT NULL,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE role (
    id_role      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom          VARCHAR(50)  NOT NULL UNIQUE,
    description  VARCHAR(255)
);

CREATE TABLE permission (
    id_permission  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code           VARCHAR(100) NOT NULL UNIQUE,
    description    VARCHAR(255)
);

CREATE TABLE utilisateur_role (
    id_utilisateur  UUID REFERENCES utilisateur(id_utilisateur) ON DELETE CASCADE,
    id_role         UUID REFERENCES role(id_role)               ON DELETE CASCADE,
    PRIMARY KEY (id_utilisateur, id_role)
);

CREATE TABLE role_permission (
    id_role        UUID REFERENCES role(id_role)             ON DELETE CASCADE,
    id_permission  UUID REFERENCES permission(id_permission) ON DELETE CASCADE,
    PRIMARY KEY (id_role, id_permission)
);

-- -----------------------------------------------------------------------------
-- Référentiels documentaires
-- -----------------------------------------------------------------------------
CREATE TABLE categorie (
    id_categorie  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom           VARCHAR(100) NOT NULL,
    description   VARCHAR(255)
);

CREATE TABLE document_type (
    id_document_type         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                      VARCHAR(150) NOT NULL,
    description              TEXT,
    id_categorie             UUID REFERENCES categorie(id_categorie),
    statut                   VARCHAR(30)  NOT NULL DEFAULT 'IMPORTE',
    version                  INT          NOT NULL DEFAULT 1,
    id_utilisateur_createur  UUID REFERENCES utilisateur(id_utilisateur),
    fichier_source_cle       VARCHAR(500),
    date_creation            TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_type_statut CHECK (statut IN (
        'IMPORTE', 'EN_EXTRACTION', 'STRUCTURE_EXTRAITE', 'EN_VALIDATION',
        'ACTIF', 'ARCHIVE', 'ECHEC_EXTRACTION'
    ))
);
CREATE INDEX idx_document_type_categorie ON document_type(id_categorie);
CREATE INDEX idx_document_type_statut    ON document_type(statut);

CREATE TABLE document_structure (
    id_structure      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document_type  UUID UNIQUE REFERENCES document_type(id_document_type) ON DELETE CASCADE,
    arbre_json        JSONB   NOT NULL,
    possede_toc       BOOLEAN NOT NULL DEFAULT FALSE
);

-- Suivi asynchrone du pipeline d'extraction (section 4.6)
CREATE TABLE extraction_job (
    id_job            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document_type  UUID NOT NULL REFERENCES document_type(id_document_type) ON DELETE CASCADE,
    statut            VARCHAR(30) NOT NULL DEFAULT 'EN_FILE',
    message_erreur    TEXT,
    date_debut        TIMESTAMP NOT NULL DEFAULT now(),
    date_fin          TIMESTAMP,
    CONSTRAINT chk_extraction_job_statut CHECK (statut IN (
        'EN_FILE', 'EN_COURS', 'TERMINE', 'ECHEC'
    ))
);
CREATE INDEX idx_extraction_job_doc ON extraction_job(id_document_type);

-- -----------------------------------------------------------------------------
-- Conversations et génération
-- -----------------------------------------------------------------------------
CREATE TABLE conversation (
    id_conversation   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_utilisateur    UUID REFERENCES utilisateur(id_utilisateur),
    id_document_type  UUID REFERENCES document_type(id_document_type),
    titre             VARCHAR(255),
    date_creation     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversation_utilisateur ON conversation(id_utilisateur);

CREATE TABLE message (
    id_message       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_conversation  UUID REFERENCES conversation(id_conversation) ON DELETE CASCADE,
    role             VARCHAR(20) NOT NULL,
    contenu          TEXT        NOT NULL,
    date_creation    TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT chk_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);
CREATE INDEX idx_message_conversation ON message(id_conversation, date_creation);

CREATE TABLE document_reference (
    id_reference      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_conversation   UUID REFERENCES conversation(id_conversation) ON DELETE CASCADE,
    nom_fichier       VARCHAR(255) NOT NULL,
    chemin_stockage   VARCHAR(500) NOT NULL,
    date_import       TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_reference_conv ON document_reference(id_conversation);

CREATE TABLE document_genere (
    id_document_genere  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_conversation     UUID REFERENCES conversation(id_conversation),
    id_document_type    UUID REFERENCES document_type(id_document_type),
    id_utilisateur      UUID REFERENCES utilisateur(id_utilisateur),
    statut              VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    langue              VARCHAR(10) NOT NULL DEFAULT 'FR',
    ton                 VARCHAR(30) NOT NULL DEFAULT 'NEUTRE',
    longueur_cible      INT,
    contenu_pivot       JSONB,
    versions_historique JSONB,
    date_creation       TIMESTAMP   NOT NULL DEFAULT now(),
    date_maj            TIMESTAMP,
    CONSTRAINT chk_document_genere_statut CHECK (statut IN (
        'BROUILLON', 'EN_GENERATION', 'GENERE', 'ECHEC',
        'EN_EDITION', 'EXPORTE', 'ARCHIVE'
    ))
);
CREATE INDEX idx_document_genere_utilisateur ON document_genere(id_utilisateur);
CREATE INDEX idx_document_genere_type        ON document_genere(id_document_type);

-- -----------------------------------------------------------------------------
-- Pipeline RAG : chunks + embeddings pgvector (section 4.5)
-- -----------------------------------------------------------------------------
CREATE TABLE document_chunk (
    id_chunk        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_reference    UUID REFERENCES document_reference(id_reference) ON DELETE CASCADE,
    index_chunk     INT  NOT NULL,
    contenu         TEXT NOT NULL,
    embedding       vector(1536),
    date_creation   TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_chunk_reference ON document_chunk(id_reference);
-- Index approximatif ivfflat : à créer après un premier chargement conséquent
-- (`ANALYZE document_chunk;` puis `CREATE INDEX ... USING ivfflat`) car il
-- exige un jeu de données pour choisir un `lists` pertinent. Laissé en commentaire
-- pour éviter l'échec au démarrage à froid :
-- CREATE INDEX idx_document_chunk_embedding
--   ON document_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- -----------------------------------------------------------------------------
-- Configuration IA, journal, notifications
-- -----------------------------------------------------------------------------
CREATE TABLE ai_model_config (
    id_config          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fournisseur        VARCHAR(30)  NOT NULL,
    nom_modele         VARCHAR(100) NOT NULL,
    reference_cle_api  VARCHAR(255),
    est_defaut         BOOLEAN      NOT NULL DEFAULT FALSE,
    actif              BOOLEAN      NOT NULL DEFAULT TRUE,
    date_creation      TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT chk_ai_fournisseur CHECK (fournisseur IN (
        'OPENAI', 'CLAUDE', 'GEMINI', 'MISTRAL', 'OLLAMA', 'DEEPSEEK'
    ))
);
-- Un seul fournisseur par défaut à la fois.
CREATE UNIQUE INDEX ux_ai_model_config_defaut
    ON ai_model_config (est_defaut)
    WHERE est_defaut = TRUE;

CREATE TABLE journal_activite (
    id_journal      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_utilisateur  UUID REFERENCES utilisateur(id_utilisateur),
    action          VARCHAR(100) NOT NULL,
    type_entite     VARCHAR(50),
    id_entite       UUID,
    date_action     TIMESTAMP    NOT NULL DEFAULT now(),
    adresse_ip      VARCHAR(45)
);
CREATE INDEX idx_journal_activite_date        ON journal_activite(date_action DESC);
CREATE INDEX idx_journal_activite_utilisateur ON journal_activite(id_utilisateur, date_action DESC);

-- Table append-only : bloque toute modification/suppression via l'API applicative.
CREATE OR REPLACE FUNCTION journal_activite_no_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'journal_activite est append-only';
END; $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_activite_no_update
BEFORE UPDATE OR DELETE ON journal_activite
FOR EACH ROW EXECUTE FUNCTION journal_activite_no_change();

CREATE TABLE notification (
    id_notification  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_utilisateur   UUID REFERENCES utilisateur(id_utilisateur),
    type             VARCHAR(50) NOT NULL,
    contenu          TEXT        NOT NULL,
    lue              BOOLEAN     NOT NULL DEFAULT FALSE,
    date_creation    TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE INDEX idx_notification_utilisateur ON notification(id_utilisateur, lue, date_creation DESC);
