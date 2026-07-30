-- =============================================================================
-- DocuAI — schéma initial (reconstruction complète, cf. prompt maître §5)
--
-- Fidèle au schéma donné en section 5 du prompt, avec les ajouts explicitement
-- demandés par le prompt lui-même (« Ajoute toi-même les tables nécessaires au
-- RAG... non détaillées explicitement... mais requises par le pipeline NLP »,
-- section 4.5) et par le pipeline d'extraction asynchrone (section 4.6), plus
-- quelques colonnes/adaptations documentées ci-dessous quand le schéma
-- indicatif de la section 5 ne suffisait pas à couvrir les fonctionnalités
-- demandées ailleurs dans le même document.
-- =============================================================================

-- pgcrypto : gen_random_uuid(). vector : type "vector" (pgvector), utilisé par
-- document_chunk plus bas. Le prompt maître écrit `CREATE EXTENSION IF NOT
-- EXISTS pgvector` mais le nom réel de l'extension Postgres est `vector`
-- (le paquet/l'image s'appelle pgvector, l'extension SQL s'appelle vector) —
-- corrigé ici, sinon la migration échoue au premier démarrage.
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
    -- Absent du schéma indicatif (section 5) mais indispensable au pipeline
    -- d'extraction (section 4.6) : référence de l'objet MinIO du fichier
    -- source importé, pour permettre un nouvel essai d'extraction sans
    -- ré-upload.
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

-- Suivi asynchrone du pipeline d'extraction (section 4.6 : "Traite l'import et
-- l'extraction de façon asynchrone (tâche en file, statut consultable)").
-- Table absente du schéma indicatif de la section 5 mais explicitement requise
-- par le texte de la section 4.6.
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

-- document_genere : quelques colonnes diffèrent volontairement du schéma
-- indicatif de la section 5 pour rester cohérentes avec le reste du prompt
-- maître et le contrat déjà stabilisé côté frontend (types/index.ts) :
--   - longueur_cible en VARCHAR (énum COURT/MOYEN/LONG/EXTENSIF, EF07) plutôt
--     qu'un INT non spécifié (nombre de mots ? de pages ?) ;
--   - contenu_pivot reste JSONB mais désigne ici le "format pivot interne"
--     assemblé par le Content Assembler (section 4.4 : "JSON structuré + HTML
--     sémantique"), pas le prompt saisi par l'utilisateur ;
--   - prompt_utilisateur (TEXT, nouveau) porte ce texte saisi par
--     l'utilisateur, distinct du pivot assemblé ci-dessus ;
--   - contenu (TEXT, nouveau) porte le rendu Markdown/HTML assemblé, consommé
--     directement par les exporteurs (docuai-export) sans reparser le JSON à
--     chaque export ;
--   - sections (JSONB, nouveau) porte l'état par section { id, label, status,
--     content } utilisé par le flux SSE (endpoint /generations/{id}/stream) ;
--   - versions_historique (JSONB, nouveau) couvre l'exigence de la section 6
--     ("garder une trace des versions précédentes en JSONB si simple à
--     faire").
CREATE TABLE document_genere (
    id_document_genere   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_conversation      UUID REFERENCES conversation(id_conversation),
    id_document_type     UUID REFERENCES document_type(id_document_type),
    id_utilisateur       UUID REFERENCES utilisateur(id_utilisateur),
    statut               VARCHAR(30) NOT NULL DEFAULT 'BROUILLON',
    langue               VARCHAR(10) NOT NULL DEFAULT 'FR',
    ton                  VARCHAR(30) NOT NULL DEFAULT 'NEUTRE',
    longueur_cible       VARCHAR(20),
    prompt_utilisateur   TEXT,
    contenu_pivot        JSONB,
    contenu              TEXT,
    sections             JSONB NOT NULL DEFAULT '[]'::jsonb,
    versions_historique  JSONB,
    date_creation        TIMESTAMP   NOT NULL DEFAULT now(),
    date_maj             TIMESTAMP,
    CONSTRAINT chk_document_genere_statut CHECK (statut IN (
        'BROUILLON', 'EN_GENERATION', 'GENERE', 'ECHEC',
        'EN_EDITION', 'EXPORTE', 'ARCHIVE'
    ))
);
CREATE INDEX idx_document_genere_utilisateur ON document_genere(id_utilisateur);
CREATE INDEX idx_document_genere_type        ON document_genere(id_document_type);

-- -----------------------------------------------------------------------------
-- Pipeline RAG : chunks + embeddings pgvector (section 4.5, table non
-- détaillée dans le schéma indicatif de la section 5 mais explicitement
-- demandée : "Ajoute toi-même les tables nécessaires au RAG").
-- 1536 = dimension des embeddings text-embedding-3-small (OpenAI), choix par
-- défaut documenté ; à ajuster par migration si un autre fournisseur
-- d'embeddings avec une autre dimension est retenu plus tard.
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
-- (`ANALYZE document_chunk;` puis choix d'un paramètre `lists` pertinent selon
-- le volume réel) — laissé en commentaire pour ne pas échouer au démarrage à
-- froid sur une table vide :
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

-- Append-only (section 8) : aucune modification/suppression via l'API
-- applicative, imposé par un trigger ci-dessous.
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
