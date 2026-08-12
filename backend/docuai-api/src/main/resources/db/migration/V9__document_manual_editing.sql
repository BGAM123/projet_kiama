-- Refonte : génération manuelle assistée par section (remplace le flux
-- conversationnel de génération de contenu, cf. note de migration
-- backend/docs/migration-generation-manuelle.md). Aucune donnée de
-- production à préserver dans document_genere à ce stade du projet -> DROP
-- plutôt que backfill. Les tables conversation/message/document_reference/
-- document_chunk (RAG) ne sont PAS touchées : elles restent en base,
-- dormantes, potentiellement réutilisables plus tard comme contexte pour le
-- bouton "Améliorer avec l'IA".
DROP TABLE IF EXISTS document_genere CASCADE;

CREATE TABLE document (
    id_document       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document_type  UUID NOT NULL REFERENCES document_type(id_document_type),
    id_utilisateur    UUID NOT NULL REFERENCES utilisateur(id_utilisateur),
    statut            VARCHAR(20) NOT NULL DEFAULT 'BROUILLON',
    langue            VARCHAR(10) NOT NULL DEFAULT 'FR',
    ton               VARCHAR(30) NOT NULL DEFAULT 'NEUTRE',
    minio_object_key  VARCHAR(500),
    export_format     VARCHAR(10),
    date_creation     TIMESTAMP NOT NULL DEFAULT now(),
    date_maj          TIMESTAMP,
    CONSTRAINT chk_document_statut CHECK (statut IN ('BROUILLON', 'FINALISE', 'ARCHIVE'))
);
CREATE INDEX idx_document_utilisateur ON document(id_utilisateur);
CREATE INDEX idx_document_type ON document(id_document_type);

CREATE TABLE document_section (
    id_document_section    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document            UUID NOT NULL REFERENCES document(id_document) ON DELETE CASCADE,
    id_parent_section      UUID REFERENCES document_section(id_document_section) ON DELETE CASCADE,
    source_node_id         VARCHAR(100),
    type                   VARCHAR(30) NOT NULL,
    level                  INT,
    label                  VARCHAR(255) NOT NULL,
    table_columns          JSONB,
    order_index            INT NOT NULL,
    user_content           TEXT,
    ai_suggested_content   TEXT,
    statut                 VARCHAR(20) NOT NULL DEFAULT 'EMPTY',
    date_maj               TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_section_type CHECK (type IN ('TITLE', 'SUBTITLE', 'SUB_SUBTITLE', 'TABLE', 'PARAGRAPH_PLACEHOLDER')),
    CONSTRAINT chk_document_section_statut CHECK (statut IN ('EMPTY', 'DRAFTED', 'AI_IMPROVED', 'FINALIZED'))
);
CREATE INDEX idx_document_section_document ON document_section(id_document, order_index);
CREATE INDEX idx_document_section_parent ON document_section(id_parent_section);

CREATE TABLE document_section_history (
    id_history           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_document_section  UUID NOT NULL REFERENCES document_section(id_document_section) ON DELETE CASCADE,
    content               TEXT NOT NULL,
    source                VARCHAR(20) NOT NULL,
    date_creation          TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_document_section_history_source CHECK (source IN ('USER', 'AI_APPLIED'))
);
CREATE INDEX idx_document_section_history_section ON document_section_history(id_document_section, date_creation);
