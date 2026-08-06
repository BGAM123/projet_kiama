-- En-tête/pied de page extraits du DOCX source, texte statique rejoué tel
-- quel à l'export (Bloc 7) — jamais généré par l'IA. NULL pour les Document
-- Types déjà importés avant cette migration (pas de backfill possible sans
-- rejouer l'extraction depuis le fichier source).
ALTER TABLE document_structure ADD COLUMN header_text TEXT;
ALTER TABLE document_structure ADD COLUMN footer_text TEXT;
