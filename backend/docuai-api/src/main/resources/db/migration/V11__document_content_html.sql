-- Refonte de la génération : le document ne se rédige plus section par
-- section, mais dans un éditeur type Word affichant d'emblée le squelette du
-- Document Type choisi. Le contenu vit donc dans un unique document HTML
-- (celui de l'éditeur), et non plus dans le Markdown de chaque
-- document_section.
--
-- HTML et pas Markdown : seul le HTML porte ce que l'éditeur expose désormais
-- (police, taille, couleur, surlignage, alignement, images intégrées, tableaux
-- à cellules fusionnées, sauts de page). Le pivot Markdown perdait tout cela
-- en silence à la sauvegarde — cf. HtmlContentParser côté docuai-export, qui
-- relit cette colonne pour produire le DOCX/PDF.
--
-- TEXT (donc TOAST côté PostgreSQL) : les images sont encodées en data URI
-- dans le document, une ligne peut légitimement peser quelques Mo.
ALTER TABLE document ADD COLUMN content_html TEXT;

-- document_section n'est pas supprimée : les documents créés avant cette
-- refonte n'ont que leur contenu Markdown par section, et restent exportables
-- tant que content_html est NULL (cf. DocumentService.buildExportContent).
COMMENT ON COLUMN document.content_html IS
    'Document complet mis en forme dans l''éditeur type Word. NULL pour les documents antérieurs, dont le contenu est reconstitué depuis document_section.';
