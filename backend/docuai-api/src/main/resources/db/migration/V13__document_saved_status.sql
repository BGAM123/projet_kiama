-- Statut intermédiaire du cycle de vie d'un document : BROUILLON (squelette
-- tout juste créé, jamais enregistré) -> SAUVEGARDE (au moins un
-- enregistrement effectué dans l'éditeur, cf. DocumentService#saveContent) ->
-- FINALISE (assemblé et exporté) -> ARCHIVE. Sans ce statut, l'Historique ne
-- pouvait pas distinguer un document dont l'utilisateur a effectivement
-- travaillé le contenu d'un squelette encore vierge — les deux affichaient
-- "Brouillon".
ALTER TABLE document DROP CONSTRAINT chk_document_statut;
ALTER TABLE document ADD CONSTRAINT chk_document_statut
    CHECK (statut IN ('BROUILLON', 'SAUVEGARDE', 'FINALISE', 'ARCHIVE'));
