-- GEMINI et DEEPSEEK étaient déjà acceptés par la contrainte (V3) mais
-- jamais seedés (adaptateurs jusqu'ici structurés, sans @Component) ; QWEN
-- (Alibaba Cloud DashScope) est un nouveau fournisseur. Les trois adaptateurs
-- sont désormais réellement branchés (@Component) — même pattern que
-- V3__add_groq_provider.sql : seedé actif=TRUE, l'absence de clé API réelle
-- est gérée au moment de l'appel (requireApiKey() dans les adaptateurs), pas
-- via ce flag.
ALTER TABLE ai_model_config DROP CONSTRAINT chk_ai_fournisseur;
ALTER TABLE ai_model_config ADD CONSTRAINT chk_ai_fournisseur CHECK (fournisseur IN (
    'OPENAI', 'CLAUDE', 'GEMINI', 'MISTRAL', 'OLLAMA', 'DEEPSEEK', 'GROQ', 'QWEN'
));

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'GEMINI', 'gemini-1.5-flash', 'GEMINI_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'GEMINI');

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'DEEPSEEK', 'deepseek-chat', 'DEEPSEEK_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'DEEPSEEK');

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'QWEN', 'qwen-plus', 'QWEN_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'QWEN');
