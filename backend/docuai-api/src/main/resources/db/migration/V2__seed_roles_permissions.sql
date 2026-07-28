-- =============================================================================
-- DocuAI — seed rôles, permissions, mapping rôle→permission, compte admin.
-- Idempotent : ré-exécutable sans effet indésirable.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Rôles standards (section 6, matrice RBAC)
-- -----------------------------------------------------------------------------
INSERT INTO role (nom, description) VALUES
    ('ADMIN',       'Administrateur — gouverne référentiels, config IA, supervision'),
    ('UTILISATEUR', 'Utilisateur — consomme les Documents Types pour générer ses documents')
ON CONFLICT (nom) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Permissions granulaires (codes utilisés par @PreAuthorize)
-- -----------------------------------------------------------------------------
INSERT INTO permission (code, description) VALUES
    -- Utilisateurs et rôles
    ('USERS_MANAGE',            'Gérer les utilisateurs (CRUD, activation)'),
    ('ROLES_MANAGE',            'Gérer les rôles et permissions'),
    -- Documents Types
    ('DOCUMENT_TYPE_READ',      'Consulter les Documents Types'),
    ('DOCUMENT_TYPE_MANAGE',    'Créer / modifier / archiver un Document Type'),
    ('DOCUMENT_TYPE_IMPORT',    'Importer un fichier source et lancer une extraction'),
    -- Catégories
    ('CATEGORY_MANAGE',         'Gérer les catégories documentaires'),
    -- Conversation & génération
    ('CONVERSATION_USE',        'Créer et utiliser des conversations IA'),
    ('DOCUMENT_GENERATE',       'Lancer une génération documentaire'),
    ('DOCUMENT_EDIT_OWN',       'Éditer ses propres documents générés'),
    ('DOCUMENT_EXPORT',         'Exporter un document généré'),
    -- Historique & dashboard
    ('HISTORY_READ_OWN',        'Consulter son propre historique'),
    ('HISTORY_READ_ALL',        'Consulter l''historique global'),
    ('DASHBOARD_READ_OWN',      'Consulter son tableau de bord personnel'),
    ('DASHBOARD_READ_ALL',      'Consulter le tableau de bord global'),
    -- Configuration IA
    ('AI_CONFIG_MANAGE',        'Configurer les fournisseurs IA (clés, modèles, défaut)'),
    -- Audit
    ('AUDIT_LOG_READ',          'Consulter et exporter le journal d''activité'),
    -- Notifications
    ('NOTIFICATION_READ_OWN',   'Consulter et marquer ses notifications')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Mapping rôle → permissions (matrice RBAC de la section 6)
-- -----------------------------------------------------------------------------

-- ADMIN : toutes les permissions
INSERT INTO role_permission (id_role, id_permission)
SELECT r.id_role, p.id_permission
FROM role r CROSS JOIN permission p
WHERE r.nom = 'ADMIN'
ON CONFLICT DO NOTHING;

-- UTILISATEUR : sous-ensemble (lecture Documents Types + génération + export + historique/dashboard/notifs perso)
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
-- Catégorie et Document Type d'exemple (livrable 10 : jeu de données de seed)
-- -----------------------------------------------------------------------------
INSERT INTO categorie (nom, description) VALUES
    ('Général', 'Catégorie par défaut'),
    ('Commercial', 'Propositions commerciales, offres, devis'),
    ('Interne', 'Comptes rendus, notes internes')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Compte administrateur de bootstrap.
-- Mot de passe par défaut : ChangeMe!2026  (À MODIFIER IMPÉRATIVEMENT en prod)
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

-- Attribution du rôle ADMIN au compte bootstrap.
INSERT INTO utilisateur_role (id_utilisateur, id_role)
SELECT u.id_utilisateur, r.id_role
FROM utilisateur u
JOIN role r ON r.nom = 'ADMIN'
WHERE u.email = 'admin@docuai.local'
ON CONFLICT DO NOTHING;
