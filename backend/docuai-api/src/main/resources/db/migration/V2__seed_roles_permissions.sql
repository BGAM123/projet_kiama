-- =============================================================================
-- DocuAI — seed rôles, permissions, mapping rôle→permission, compte admin,
-- catégorie + Document Type d'exemple (livrable §13.10 du prompt maître).
-- Idempotent : ré-exécutable sans effet indésirable (ON CONFLICT DO NOTHING).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Rôles standards (section 6 : matrice RBAC, deux rôles livrés en standard,
-- extensibles par l'administrateur via /api/v1/roles)
-- -----------------------------------------------------------------------------
INSERT INTO role (nom, description) VALUES
    ('ADMIN',       'Administrateur — gouverne référentiels, config IA, supervision'),
    ('UTILISATEUR', 'Utilisateur — consomme les Documents Types pour générer ses documents')
ON CONFLICT (nom) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Permissions granulaires (codes utilisés par @PreAuthorize("hasAuthority(...)"))
-- Une permission par ligne de la matrice RBAC de la section 6.
-- -----------------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    -- Utilisateurs et rôles
    ('USERS_MANAGE',            'Gérer les utilisateurs (CRUD, activation/désactivation, affectation de rôles)'),
    ('ROLES_MANAGE',            'Gérer les rôles et permissions granulaires'),
    -- Documents Types & extraction
    ('DOCUMENT_TYPE_IMPORT',    'Importer un fichier source (.docx/.pdf) pour un Document Type'),
    ('DOCUMENT_TYPE_EXTRACT',   'Lancer/relancer une extraction de structure'),
    ('DOCUMENT_TYPE_MANAGE',    'Créer / modifier / supprimer (archiver) un Document Type, corriger sa structure'),
    ('DOCUMENT_TYPE_READ',      'Consulter les Documents Types (lecture seule)'),
    -- Catégories
    ('CATEGORY_MANAGE',         'Gérer les catégories documentaires'),
    -- Conversation & génération
    ('CONVERSATION_USE',        'Démarrer et utiliser des conversations IA'),
    ('DOCUMENT_GENERATE',       'Lancer une génération documentaire'),
    ('DOCUMENT_EDIT_OWN',       'Éditer ses propres documents générés (sauvegarde du contenu)'),
    ('DOCUMENT_EXPORT',         'Exporter un document généré (DOCX/PDF/Markdown)'),
    -- Historique & dashboard
    ('HISTORY_READ_OWN',        'Consulter son propre historique'),
    ('HISTORY_READ_ALL',        'Consulter l''historique global'),
    ('DASHBOARD_READ_OWN',      'Consulter son tableau de bord personnel'),
    ('DASHBOARD_READ_ALL',      'Consulter le tableau de bord global'),
    -- Configuration IA
    ('AI_CONFIG_MANAGE',        'Configurer les fournisseurs IA (clés, modèles, quotas, défaut)'),
    -- Audit
    ('AUDIT_LOG_READ',          'Consulter et exporter (CSV) le journal d''activité'),
    -- Notifications
    ('NOTIFICATION_READ_OWN',   'Consulter et marquer ses propres notifications')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Mapping rôle → permissions (matrice RBAC de la section 6, colonne par colonne)
-- -----------------------------------------------------------------------------

-- ADMIN : toutes les permissions.
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission
FROM role r CROSS JOIN permission p
WHERE r.nom = 'ADMIN'
ON CONFLICT DO NOTHING;

-- UTILISATEUR : sous-ensemble conforme à la colonne "Utilisateur" de la
-- matrice (lecture Documents Types, conversation/génération/édition/export
-- sur ses propres ressources, historique/dashboard personnels, notifications).
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission
FROM role r
JOIN permission p ON p.code IN (
    'DOCUMENT_TYPE_READ',
    'CONVERSATION_USE',
    'DOCUMENT_GENERATE',
    'DOCUMENT_EDIT_OWN',
    'DOCUMENT_EXPORT',
    'HISTORY_READ_OWN',
    'DASHBOARD_READ_OWN',
    'NOTIFICATION_READ_OWN'
)
WHERE r.nom = 'UTILISATEUR'
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Catégories de base
-- -----------------------------------------------------------------------------
INSERT INTO categorie (nom, description) VALUES
    ('Général',    'Catégorie par défaut'),
    ('Commercial', 'Propositions commerciales, offres, devis'),
    ('Interne',    'Comptes rendus, notes internes')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Compte administrateur de bootstrap (livrable §13.10).
-- Mot de passe par défaut : ChangeMe!2026  (À CHANGER IMPÉRATIVEMENT en prod).
-- Hash BCrypt généré via `crypt()` (extension pgcrypto), compatible
-- BCryptPasswordEncoder de Spring Security.
-- -----------------------------------------------------------------------------
INSERT INTO utilisateur (email, mot_de_passe_hash, prenom, nom, actif)
VALUES (
    'admin@docuai.local',
    crypt('ChangeMe!2026', gen_salt('bf', 10)),
    'Admin',
    'DocuAI',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO utilisateur_role (id_utilisateur, id_role)
SELECT u.id_utilisateur, r.id_role
FROM utilisateur u
JOIN role r ON r.nom = 'ADMIN'
WHERE u.email = 'admin@docuai.local'
ON CONFLICT DO NOTHING;

-- Un second compte non-admin, pratique pour tester le RBAC (403 attendus sur
-- les routes ADMIN) sans avoir à créer un utilisateur manuellement au premier
-- lancement.
INSERT INTO utilisateur (email, mot_de_passe_hash, prenom, nom, actif)
VALUES (
    'utilisateur@docuai.local',
    crypt('ChangeMe!2026', gen_salt('bf', 10)),
    'Utilisateur',
    'Test',
    TRUE
)
ON CONFLICT (email) DO NOTHING;

INSERT INTO utilisateur_role (id_utilisateur, id_role)
SELECT u.id_utilisateur, r.id_role
FROM utilisateur u
JOIN role r ON r.nom = 'UTILISATEUR'
WHERE u.email = 'utilisateur@docuai.local'
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Document Type d'exemple avec structure (livrable §13.10 : "un Document Type
-- d'exemple avec structure"). Statut ACTIF directement (pas besoin de rejouer
-- l'extraction pour pouvoir tester une génération dès le premier démarrage).
-- -----------------------------------------------------------------------------
INSERT INTO document_type (id_document_type, nom, description, id_categorie, statut, version)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Proposition commerciale',
    'Document Type d''exemple livré en seed pour tester la génération sans passer par un import manuel au préalable.',
    c.id_categorie,
    'ACTIF',
    1
FROM categorie c
WHERE c.nom = 'Commercial'
ON CONFLICT (id_document_type) DO NOTHING;

INSERT INTO document_structure (id_document_type, arbre_json, possede_toc)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    '[
        {"id": "n1", "type": "cover", "label": "Proposition commerciale"},
        {"id": "n2", "type": "heading", "level": 1, "label": "Contexte et objectifs"},
        {"id": "n3", "type": "heading", "level": 1, "label": "Présentation de l''offre"},
        {"id": "n4", "type": "table", "label": "Détail tarifaire", "columns": ["Prestation", "Quantité", "Prix unitaire", "Total"]},
        {"id": "n5", "type": "heading", "level": 1, "label": "Modalités et conditions"},
        {"id": "n6", "type": "heading", "level": 1, "label": "Conclusion"}
    ]'::jsonb,
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM document_structure WHERE id_document_type = '00000000-0000-0000-0000-000000000001'::uuid
);

-- -----------------------------------------------------------------------------
-- Configuration IA de base (nécessaire dès le Bloc 5, seedée ici pour que
-- /api/v1/ai-configs ne soit jamais vide au premier démarrage).
-- -----------------------------------------------------------------------------
INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'OPENAI', 'gpt-4o-mini', 'OPENAI_API_KEY', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config);

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'CLAUDE', 'claude-3-5-haiku-latest', 'ANTHROPIC_API_KEY', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'CLAUDE');

INSERT INTO ai_model_config (fournisseur, nom_modele, reference_cle_api, est_defaut, actif)
SELECT 'OLLAMA', 'llama3', 'OLLAMA_BASE_URL', FALSE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE fournisseur = 'OLLAMA');
