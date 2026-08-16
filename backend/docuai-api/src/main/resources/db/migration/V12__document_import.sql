-- Import direct d'un fichier existant (.docx/.pdf/.md/.txt/.doc) vers
-- l'éditeur type Word, sans passer par un Document Type : l'utilisateur
-- dépose un fichier, il en récupère immédiatement le contenu dans l'éditeur
-- pour le modifier et l'exporter.
--
-- id_document_type devient nullable : un document importé n'a pas de
-- gabarit — son plan n'est pas hérité d'un Document Type, il vient
-- entièrement du fichier déposé.
ALTER TABLE document ALTER COLUMN id_document_type DROP NOT NULL;

-- Titre affiché/utilisé pour le nom de fichier à l'export. Pour un document
-- créé depuis un Document Type, NULL : le titre reste celui du Document
-- Type (comportement historique, DocumentService#resolvedTitle). Pour un
-- document importé, dérivé du nom du fichier déposé.
ALTER TABLE document ADD COLUMN titre VARCHAR(255);
