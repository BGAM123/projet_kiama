# DocuAI — Génération documentaire par IA

Plateforme permettant d'extraire la structure de documents existants (Documents Types), puis de générer, via une interface de chat IA, de nouveaux documents professionnels respectant cette structure, avant édition WYSIWYG et export.

> **Intégration partielle avec le backend réel (2026-07-29).** Auth, liste des
> utilisateurs, upload/extraction, export, notifications et journal d'activité
> passent maintenant par le vrai backend Spring Boot. Le reste (catégories,
> Documents Types CRUD, conversations, générations avec streaming, dashboard,
> config IA) continue de résoudre contre la couche mock (`lib/api/fixtures.ts`)
> faute d'endpoints backend — voir `../rapport-ecarts-integration.md` pour le
> détail exact, écran par écran.

## Lancer le projet

### Seul (données mockées uniquement)

```bash
npm install
npm run dev
```

L'application démarre sur http://localhost:3000 et fonctionne entièrement en
mémoire — utile pour travailler sur l'UI sans backend.

### Avec le backend réel

1. Démarrer le backend (voir `../backend/README.md`) — Postgres/Redis/MinIO
   via `docker compose`, puis `mvn -pl docuai-api -am spring-boot:run`.
2. Vérifier `.env.local` (créé par cette intégration) :
   ```
   NEXT_PUBLIC_API_URL=http://localhost:8080/api/v1
   ```
3. `npm install && npm run dev`.
4. Se connecter avec le compte de bootstrap backend : `admin@docuai.local` /
   `ChangeMe!2026` (les identifiants de démo `admin@docuai.io` ci-dessous ne
   fonctionnent que pour les écrans encore mockés, ils n'existent pas dans la
   base réelle).

Le login, la liste des utilisateurs (lecture), l'upload/extraction de
fichier, l'export de document, les notifications et le journal d'activité
utilisent le backend réel. Tous les autres écrans (catégories, Documents
Types, chat/conversations, génération avec streaming, dashboard, config IA,
et la création/édition/suppression d'utilisateurs) restent mockés — le
backend n'expose pas encore ces endpoints.

### Identifiants de démonstration (mode mock uniquement)

| Rôle | E-mail | Mot de passe |
|---|---|---|
| Administrateur | `admin@docuai.io` | `demo1234` |
| Utilisateur | `user@docuai.io` | `demo1234` |

Cliquez sur une carte d'identifiant sur l'écran de connexion pour pré-remplir le formulaire.

## Variables d'environnement

| Variable | Description |
|---|---|
| `NEXT_PUBLIC_API_URL` | URL de base du backend Spring Boot (`.env.local`, défaut `http://localhost:8080/api/v1`) |

Il n'y a pas de variable de bascule mock/réel globale (`NEXT_PUBLIC_USE_MOCK_API`) :
la bascule se fait fonction par fonction dans `lib/api/client.ts` (chaque
fonction migrée a un commentaire l'indiquant), pas au niveau de toute
l'application — le périmètre connecté n'étant qu'une partie des écrans à ce
stade.

## Limitation connue de cette intégration

Les changements de cette itération ont été revus statiquement (imports,
types, cohérence) mais **je n'ai pas pu exécuter `npm install` /
`npm run typecheck` / `npm run build` de façon fiable dans l'environnement où
ces changements ont été produits** : le point de montage utilisé refusait les
opérations de renommage atomiques dont `npm install` a besoin (erreurs
`ENOTEMPTY` / `EPERM` récurrentes, indépendantes du contenu du projet).
**Lancez `npm install && npm run typecheck` en local avant de considérer ces
changements comme définitivement validés.**

## Fonctionnalités (V1)

- **Authentification** simulée (JWT en mémoire) avec routes protégées par rôle.
- **Tableau de bord** : KPIs, activité sur 7 jours, répartition par catégorie, documents récents.
- **Documents Types** : liste filtrable/triable, import drag & drop avec extraction simulée, prévisualisation et édition de la structure arborescente, validation.
- **Chat IA** : liste des conversations, bulles de messages, panneau de paramètres (Document Type, langue, ton, longueur, documents de référence), **génération en streaming section par section** avec progression.
- **Éditeur WYSIWYG** (TipTap) : barre d'outils, tableaux, listes, sauvegarde automatique, contrôle de conformité à la structure, **export DOCX / PDF / Markdown** (téléchargement simulé).
- **Historique** : liste chronologique filtrable (date, catégorie, statut, recherche texte).
- **Administration** : utilisateurs (création, activation/désactivation, rôles), rôles & matrice de permissions, modèles IA (6 fournisseurs, activation, fournisseur par défaut, clé API masquée).
- **Journal d'activité** : table paginée et filtrable.
- **Notifications** : cloche avec compteur dans le header + page dédiée, marquage lu/non lu.
- **Mode clair / sombre** et design responsive.

## Stack technique

- Next.js 13 (App Router) + React 18 + TypeScript
- Tailwind CSS + shadcn/ui (design system)
- TanStack React Query (cache, états serveur)
- React Hook Form + Zod (formulaires et validation)
- TipTap (éditeur WYSIWYG)
- Zustand (session d'authentification, persistée)
- Recharts (graphiques)
- lucide-react (icônes), sonner (toasts)

## Architecture & couche API simulée

Toute la logique d'accès aux données se trouve dans `lib/api/`. Chaque fonction de `lib/api/client.ts` reproduit **exactement les contrats REST** de la section 4 (mêmes URLs conceptuelles, mêmes verbes, même forme d'enveloppe `{ data, error, meta }`) mais résout les données depuis les fixtures en mémoire (`lib/api/fixtures.ts`) avec une latence simulée.

### Brancher un vrai backend plus tard

Pour passer en production, remplacez uniquement le **corps** des fonctions de `lib/api/client.ts` par des appels Axios vers les mêmes endpoints. Les signatures, les types de retour et l'enveloppe restent identiques — **aucun composant n'a à être réécrit**.

Fichiers à toucher :
1. `lib/api/client.ts` — remplacer chaque `guard(() => ...)` par un appel `axios.get/post/put/delete('/api/v1/...')`.
2. (Optionnel) `lib/api/fixtures.ts` — à supprimer une fois le backend en place.
3. (Optionnel) `lib/auth-store.ts` — brancher `refresh` sur l'endpoint réel de rafraîchissement de jeton.

Le streaming de génération (`lib/api/generator.ts`) reproduit un flux SSE section par section ; il pourra être remplacé par un vrai `EventSource` sur `/api/v1/generations/{id}/stream`.

## Structure du projet

```
app/
  (app)/                # Routes authentifiées (layout avec sidebar + guard)
    dashboard/          # Tableau de bord
    documents-types/    # Liste + import + [id]/structure (prévisualisation)
    chat/               # Interface Chat IA
    editor/[id]/        # Éditeur TipTap + export
    history/            # Historique
    notifications/      # Notifications
    admin/              # users, roles, ai-models
  login/                # Connexion
  access-denied/        # Accès refusé (rôle insuffisant)
components/             # Composants partagés + ui/ (shadcn)
lib/
  api/                  # client.ts (mock) + fixtures.ts + generator.ts (streaming)
  hooks/queries.ts      # Hooks React Query typés
  auth-store.ts         # Zustand (session JWT)
  format.ts             # Libellés de statuts, formats de date
  utils.ts              # cn()
types/index.ts          # Tous les types TypeScript du domaine
```

## Rôles et permissions

- **ADMIN** : accès complet (Documents Types, administration, journal d'activité, configuration IA).
- **UTILISATEUR** : génère des documents à partir des Documents Types actifs.

Les routes d'administration redirigent automatiquement un utilisateur simple vers une page « Accès refusé ».
