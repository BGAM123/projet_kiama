-- Ajoute GROQ à la liste des fournisseurs IA supportés (Bloc 5/8) et seede
-- les configurations Mistral (déjà supporté par la contrainte mais jamais
-- seedé en V2) et Groq — actives par défaut comme OPENAI/CLAUDE/OLLAMA en
-- V2 (l'absence de clé API réelle est gérée au moment de l'appel, pas via
-- ce flag ; voir requireApiKey() dans AbstractOpenAiStyleAdapter).
ALTER TABLE ai_model_config DROP CONSTRAINT chk_ai_fournisseur;
ALTER TABLE ai_model_config ADD CONSTRAINT chk_ai_fournisseur CHECK (fournisseur IN (
    'OPENAI', 'CLAUDE', 'GEMINI', 'MISTRAL', 'OLLAMA', 'DEEPSEEK', 'GROQ'
));

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'MISTRAL', 'mistral-small-latest', 'MISTRAL_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'MISTRAL');

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'GROQ', 'llama-3.3-70b-versatile', 'GROQ_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'GROQ');
