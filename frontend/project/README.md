# DocuAI — Génération documentaire par IA

Plateforme permettant d'extraire la structure de documents existants (Documents Types), puis de générer, via une interface de chat IA, de nouveaux documents professionnels respectant cette structure, avant édition WYSIWYG et export.

> **Démo frontend fonctionnelle de bout en bout.** L'application s'appuie sur une couche API simulée (latence + fixtures) — aucun backend réel n'est requis pour la lancer.

## Lancer le projet

```bash
npm install
npm run dev
```

L'application démarre sur http://localhost:3000. Vous êtes redirigé vers l'écran de connexion.

### Identifiants de démonstration

| Rôle | E-mail | Mot de passe |
|---|---|---|
| Administrateur | `admin@docuai.io` | `demo1234` |
| Utilisateur | `user@docuai.io` | `demo1234` |

Cliquez sur une carte d'identifiant sur l'écran de connexion pour pré-remplir le formulaire.

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
    admin/              # users, roles, ai-models, audit
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
