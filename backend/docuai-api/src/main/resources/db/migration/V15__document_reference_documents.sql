-- Permet d'attacher un document_reference (RAG, déjà utilisé par les
-- conversations) directement à un Document en cours d'édition, plutôt qu'à
-- une Conversation — anticipé par le commentaire de V9 ("potentiellement
-- réutilisables plus tard comme contexte pour le bouton Améliorer avec
-- l'IA"). Un document_reference appartient à l'un OU l'autre, jamais les
-- deux ni aucun des deux.
ALTER TABLE document_reference
    ADD COLUMN id_document UUID REFERENCES document(id_document) ON DELETE CASCADE;

CREATE INDEX idx_document_reference_document ON document_reference(id_document);

ALTER TABLE document_reference
    ADD CONSTRAINT chk_document_reference_owner CHECK (
        (id_conversation IS NOT NULL)::int + (id_document IS NOT NULL)::int = 1
    );
