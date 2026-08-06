-- Résumé glissant de la fenêtre de contexte envoyée au LLM (ConversationService)
-- : évite de renvoyer l'historique complet à chaque tour. context_summary
-- couvre tous les messages jusqu'à context_summary_upto_message_id (inclus,
-- pas de FK stricte : le message peut être supprimé indépendamment sans
-- invalider le résumé déjà produit). Les deux colonnes restent NULL tant que
-- la conversation n'a pas dépassé la fenêtre de messages récents conservée
-- telle quelle (docuai.conversation.keep-last-messages).
ALTER TABLE conversation ADD COLUMN context_summary TEXT;
ALTER TABLE conversation ADD COLUMN context_summary_upto_message_id UUID;
