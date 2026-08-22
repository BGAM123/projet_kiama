-- reference_cle_api (depuis V1) n'a jamais été qu'un POINTEUR (ex. nom de
-- variable d'environnement) — jamais la clé API elle-même. Ces deux colonnes
-- permettent désormais de stocker une vraie clé, saisie depuis l'admin UI
-- (AiConfigService.update), chiffrée AES-256-GCM par ApiKeyCipherService
-- (docuai-ai-orchestration) avant écriture : cle_api_chiffree ne contient
-- jamais de clé en clair, cle_api_apercu ne garde que les 4 derniers
-- caractères pour un affichage masqué ("•••• ab12") sans déchiffrement.
ALTER TABLE ai_model_config
    ADD COLUMN cle_api_chiffree TEXT,
    ADD COLUMN cle_api_apercu VARCHAR(8);
