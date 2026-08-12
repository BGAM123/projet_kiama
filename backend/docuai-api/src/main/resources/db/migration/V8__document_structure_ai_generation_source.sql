-- Traçabilité de l'origine d'une structure de Document Type : import de
-- fichier + extraction déterministe (Bloc 4, comportement historique) ou
-- nouveau flux "décrire en texte -> squelette généré par IA". Permet à l'UI
-- de proposer la bonne action de relance ("relancer l'extraction" vs
-- "relancer la génération IA") et ne casse aucune structure existante :
-- toutes deviennent IMPORTED par défaut (comportement historique, aucune
-- n'a jamais été générée par IA avant cette migration).
ALTER TABLE document_structure ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'IMPORTED';
ALTER TABLE document_structure ADD CONSTRAINT chk_document_structure_source
    CHECK (source IN ('IMPORTED', 'AI_GENERATED'));
