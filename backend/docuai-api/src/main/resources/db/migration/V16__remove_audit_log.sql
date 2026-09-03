-- Retrait de la fonctionnalité "journal d'activité" : jamais implémentée côté
-- application (aucun writer/reader Java ne l'a jamais utilisée, cf.
-- backend/README.md) — seule la table de V1__init_schema.sql restait comme
-- stub vide. On ne modifie pas V2 (déjà appliqué, checksum Flyway) : la
-- permission qu'il seedait est retirée ici à la place.
DROP TRIGGER IF EXISTS trg_journal_activite_no_update ON journal_activite;
DROP FUNCTION IF EXISTS journal_activite_no_change();
DROP TABLE IF EXISTS journal_activite;

-- ON DELETE CASCADE sur role_permission.id_permission (V1) : les éventuelles
-- lignes de mapping associées disparaissent avec la permission elle-même.
DELETE FROM permission WHERE code = 'AUDIT_LOG_READ';
