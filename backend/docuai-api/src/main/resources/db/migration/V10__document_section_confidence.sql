-- Score de confiance par section, affiché dans l'éditeur de squelette (pastille
-- par section + score global du document, guide des seuils §3.4). Le score est
-- auto-déclaré par le modèle en même temps que la suggestion de reformulation
-- (cf. SectionImprovementResponseParser) : il qualifie la dernière sortie IA de
-- la section, pas le texte écrit à la main -> NULL tant qu'aucune suggestion
-- n'a été produite, remis à NULL dès que l'utilisateur réécrit la section ou
-- rejette la suggestion.
-- DOUBLE PRECISION et pas NUMERIC(5,2) : l'entité expose un Double, et la
-- validation de schéma Hibernate au démarrage (ddl-auto=validate) exige
-- float(53) — un NUMERIC fait échouer le boot de l'application.
ALTER TABLE document_section ADD COLUMN confidence_score DOUBLE PRECISION;

ALTER TABLE document_section ADD CONSTRAINT chk_document_section_confidence
    CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 100));
