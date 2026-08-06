-- Résultat de l'export automatique vers MinIO déclenché à la fin d'une
-- génération intégralement réussie (GenerationStreamService#exportToObjectStorage)
-- : clé de l'objet dans bucket-exports, format produit. NULL tant qu'aucun
-- export n'a encore réussi (échec MinIO dégradé, ou document généré avant
-- cette migration) — l'export manuel (POST /api/v1/export) reste disponible
-- indépendamment de ces colonnes.
ALTER TABLE document_genere ADD COLUMN minio_object_key VARCHAR(500);
ALTER TABLE document_genere ADD COLUMN export_format VARCHAR(10);
