# Rapport d'écarts frontend / backend — DocuAI

Date : 2026-07-29
Périmètre : `frontend` (Next.js 13.5.1, App Router) vs `backend` (Spring Boot multi-module Maven)
Étape : 3/5 — à valider avant toute modification de code.

---

## 0. Constat préalable — bloquant, à traiter avant tout le reste

Avant même de parler d'écarts de contrat, deux faits changent la nature de la mission :

**a) Le backend ne démarre pas en l'état.** `docuai-api/pom.xml` référence `com.docuai.api.DocuAiApplication` comme classe principale Spring Boot, mais cette classe n'existe nulle part dans le code (aucun `@SpringBootApplication` trouvé dans les 57 fichiers Java du dépôt). Ni `mvn package`, ni `docker compose up` ne peuvent produire un backend fonctionnel tant que cette classe n'est pas créée.

**b) La quasi-totalité des fonctionnalités métier attendues par le frontend n'existe pas côté backend.** Le frontend (via ses mocks) attend une API complète : catégories, Documents Types (CRUD + structure), conversations IA, génération de documents avec streaming, configuration des modèles IA, dashboard, journal d'audit paginé. Le backend réel n'expose que 8 contrôleurs très basiques : auth (login/refresh), users (lecture seule), roles (lecture seule), un proxy IA synchrone générique, un endpoint d'upload/extraction, un endpoint d'export, des logs d'activité, et des notifications. **Il n'y a aucun contrôleur pour les catégories, les Documents Types, les conversations, les générations, la config IA ou le dashboard.** Le README backend revendique des blocs (RAG, streaming SSE, conversations) qui ne sont pas implémentés dans le code.

**Conséquence directe sur la suite de la mission** : ce n'est pas un problème d'alignement de contrat (renommer des champs, ajuster une enveloppe JSON) mais, pour la majorité des écrans, une absence pure et simple d'implémentation backend. La section "Décision" ci-dessous distingue donc systématiquement deux cas :
- **Écart de contrat** (endpoint existe des deux côtés mais diverge) → correction rapide, dans le périmètre normal d'une mission d'intégration.
- **Fonctionnalité absente côté backend** → nécessite un développement backend complet (hors périmètre d'une simple "connexion" front↔back), signalé explicitement.

Je vous demande de trancher sur le traitement de ce second cas avant que je poursuive (voir question à la fin du rapport).

---

## 1. Tableau d'écarts par catégorie

### URL / routing — endpoints absents ou différents

| # | Écart | Frontend attend | Backend expose | Fichiers concernés | Impact | Correction proposée |
|---|---|---|---|---|---|---|
| 1.1 | Catégories : aucun contrôleur | `GET/POST/PATCH/DELETE /api/v1/categories` | Rien | FE: `lib/api/client.ts:198-226` · BE: absent | Aucun écran actuel ne consomme ces routes (fonctions mock non branchées à l'UI) | Aucune urgence : pas d'écran UI existant. À développer seulement si un écran d'admin catégories est prévu. |
| 1.2 | Documents Types : aucun contrôleur CRUD/structure | `GET/POST/PATCH/DELETE /api/v1/document-types`, `GET/PUT /api/v1/document-types/{id}/structure`, `POST /api/v1/document-types/import` | Rien (seul `POST /api/v1/documents/upload` existe, contrat différent : extraction brute, pas de notion de "Document Type" ni de structure) | FE: `documents-types/*`, `client.ts:232-293` · BE: absent | **Bloquant** : écran central de l'app (import, structure, activation) | Développement backend complet requis. Le endpoint `upload` existant peut servir de brique bas niveau (extraction Tika) mais il manque tout le modèle Document Type/Structure/Catégorie et les endpoints associés. |
| 1.3 | Conversations & messages : aucun contrôleur | `GET/POST /api/v1/conversations`, `GET/POST /api/v1/conversations/{id}/messages`, `.../reference-documents` | Rien | FE: `chat/page.tsx`, `client.ts:299-340` · BE: absent | **Bloquant** : écran chat entier | Développement backend complet requis. |
| 1.4 | Générations : aucun contrôleur, seul un proxy IA générique existe | `POST /api/v1/generations`, `GET/PATCH /api/v1/generations/{id}` | `POST /api/v1/ai/generate` (prompt→réponse synchrone unique, aucune notion de document/sections/statut) | FE: `client.ts:346-398` · BE: `AiController` | **Bloquant** : cœur métier de l'app | Développement backend complet requis (modèle `GeneratedDocument`, sections, statuts). Le endpoint `/ai/generate` peut être réutilisé en interne comme brique IA bas niveau. |
| 1.5 | Streaming génération : aucun endpoint | flux d'événements `progress/section/done` | Rien (aucun SSE/WebSocket dans le code, malgré le README) | FE: `lib/api/generator.ts` · BE: absent | **Bloquant** pour l'expérience "génération en direct" | Développement backend complet requis (SSE via `SseEmitter` recommandé, cf. §4.5 du cahier des charges). |
| 1.6 | Config IA (admin) : aucun contrôleur | `GET /api/v1/ai-configs`, `PATCH /api/v1/ai-configs/{id}` | Rien | FE: `admin/ai-models/page.tsx` | Écran admin non fonctionnalisable | Développement backend requis. |
| 1.7 | Dashboard : aucun contrôleur | `GET /api/v1/dashboard/stats` | Rien | FE: `dashboard/page.tsx` | Écran d'accueil non fonctionnalisable | Développement backend requis. |
| 1.8 | Liste des générations d'un utilisateur : absente même côté mock | `GET /api/v1/generations?userId=` (implicite, contournement direct des fixtures côté FE) | Rien | FE: `history/page.tsx:27-36`, `dashboard/page.tsx:46-56` | Historique non fonctionnalisable | À créer des deux côtés (le mock lui-même n'a pas cette fonction propre — dette pré-existante côté frontend, pas seulement un écart front/back). |
| 1.9 | Users : seules les routes de lecture existent | `POST/PATCH/DELETE /api/v1/users` | Seuls `GET /api/v1/users` et `GET /api/v1/users/{id}` | FE: `admin/users/page.tsx` · BE: `UserController` | Écran admin utilisateurs : création/édition/suppression impossibles | Développement backend requis (CRUD complet + reset password). |
| 1.10 | Notifications : chemin et type d'ID différents | `GET /api/v1/notifications?userId=` (string), `PATCH /api/v1/notifications/{id}` | `GET /api/v1/notifications/user/{userId}` (path param, `Long`), `PUT /api/v1/notifications/{id}/read` (`Long`) | FE: `client.ts:466-477` · BE: `NotificationController` | Écart de contrat pur (endpoint existe des deux côtés) | Aligner le frontend sur le backend réel : adapter l'appel en path param, `PUT` au lieu de `PATCH`, gérer un ID numérique (`Long`) plutôt qu'une chaîne libre — **à condition que le backend migre bien vers de vrais identifiants numériques cohérents avec le reste de l'app (voir §"Types de données" 1.14)**. |
| 1.11 | Audit logs : chemin différent, pas de query params | `GET /api/v1/audit-logs` (pagination/tri côté client actuellement) | `GET /api/v1/admin/logs` (10 derniers logs fixes, pas de pagination serveur) | FE: `admin/audit/page.tsx` · BE: `ActivityLogController` | Écran partiellement fonctionnel mais historique tronqué à 10 entrées | Aligner le frontend sur `/api/v1/admin/logs` ; ajouter côté backend la pagination avant mise en prod (hors périmètre strict d'intégration, à signaler). |
| 1.12 | Auth password : absent | `POST /api/v1/auth/password`, `PATCH /api/v1/users/{id}/password` | Rien | FE: `client.ts:171-192` | Changement de mot de passe non fonctionnel (fonction même pas branchée à l'UI pour le reset admin) | Développement backend requis pour le changement de mot de passe utilisateur (fonctionnalité minimale attendue). |
| 1.13 | Export : le frontend ne l'appelle jamais | — | `POST /api/v1/export` existe et fonctionne (retourne un vrai binaire) | FE: export simulé localement (Blob factice) · BE: `ExportController` | Le frontend "triche" actuellement (télécharge du texte brut renommé en .pdf/.docx) alors qu'un vrai endpoint existe | **Corriger le frontend** : brancher `handleExport` sur `POST /api/v1/export` au lieu de la simulation locale. |
| 1.14 | Upload document : le frontend ne l'appelle jamais réellement | Le frontend n'envoie que le nom de fichier, jamais de binaire | `POST /api/v1/documents/upload` existe (multipart, champ `file`, extraction Tika réelle) | FE: `documents-types/import/page.tsx` · BE: `DocumentController` | L'import de Document Type est aujourd'hui 100% factice côté frontend | **Corriger le frontend** : implémenter un vrai `FormData` vers cet endpoint (voir aussi §Upload). |

### Nommage des champs

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 2.1 | `GET /api/v1/users` renvoie l'entité JPA brute en français | `idUtilisateur, prenom, nom, motDePasseHash, actif, dateCreation` au lieu de `id, firstName, lastName, email, active, createdAt` attendus par le type `User` du frontend | BE: `docuai-core/.../model/Utilisateur.java`, `UserController.java` | Le frontend ne peut rien afficher (champs `undefined`) ; **fuite de sécurité** : le hash bcrypt du mot de passe est exposé dans la réponse JSON | **Corriger le backend** : créer/utiliser un vrai `UserDTO` (celui utilisé par `AuthController` existe déjà — `docuai-api/.../dto/UserDTO.java`) au lieu de sérialiser l'entité JPA. Retirer `motDePasseHash` de toute sérialisation. Le cahier des charges impose camelCase anglais côté API ; le backend a déjà cette convention pour `AuthController`, donc on aligne `UserController`/`RoleController` sur ce précédent plutôt que le frontend. |
| 2.2 | `GET /api/v1/roles` renvoie l'entité JPA brute en français | `idRole, nom` au lieu de `id, name` (type `Role` du frontend) | BE: `docuai-core/.../model/Role.java`, `RoleController.java` | Idem : incohérence de schéma entre `/auth/login` (qui renvoie déjà des `RoleDTO` propres) et `/api/v1/roles` | **Corriger le backend** : utiliser `RoleDTO` existant au lieu de l'entité brute. |
| 2.3 | Deux schémas incompatibles pour le même objet "utilisateur" selon l'endpoint | `/auth/login` → `UserDTO` (camelCase anglais) vs `/api/v1/users` → `Utilisateur` (français) | BE: `AuthController` vs `UserController` | Le frontend ne peut pas réutiliser un seul type `User` pour les deux réponses | **Corriger le backend** (uniformiser sur `UserDTO`/`RoleDTO` partout) — le frontend est déjà conçu camelCase anglais et n'a pas à changer. |

### Enveloppe de réponse

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 3.1 | Enveloppe `{data, error, meta}` appliquée seulement à 3 contrôleurs sur 8 | `auth`, `users`, `roles` renvoient `ApiResponse<T>` ; `ai`, `documents`, `export`, `admin/logs`, `notifications` renvoient l'objet/la liste brute | BE: tous les contrôleurs, cf. tableau §3 de l'audit backend | Le frontend (conçu pour toujours lire `response.data`) devra gérer deux formats différents selon la route, ou échouera silencieusement | **Corriger le backend**, conformément au cahier des charges qui impose l'enveloppe partout : ajouter un `ResponseBodyAdvice` global qui enveloppe systématiquement dans `ApiResponse<T>` (plutôt que modifier chaque contrôleur un par un). Cas particulier de `ExportController` (retourne un binaire `byte[]`) : à exclure explicitement de ce wrapper (les téléchargements de fichiers ne s'enveloppent pas en JSON). |

### Format des erreurs

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 4.1 | Le frontend attend `{error:{code,message,timestamp,path}}` ; le backend s'en approche mais deux chemins d'erreur différents | `GlobalExceptionHandler` produit ce format ; mais `JwtAuthenticationEntryPoint` (filtre de sécurité, court-circuite le `@ControllerAdvice`) produit un format proche mais sans `timestamp` explicite | BE: `docuai-api/.../exception/GlobalExceptionHandler.java`, `docuai-security/.../jwt/JwtAuthenticationEntryPoint.java` | Le parsing d'erreur frontend (toasts) pourrait planter sur les 401 émis par le filtre de sécurité si `timestamp` est absent et que le code frontend y accède sans garde | **Corriger le backend** : faire produire par `JwtAuthenticationEntryPoint` exactement la même forme que `GlobalExceptionHandler` (réutiliser la même classe utilitaire de construction d'erreur). |
| 4.2 | Messages d'erreur de validation non détaillés | `MethodArgumentNotValidException` → message générique, pas de détail par champ malgré `include-binding-errors: always` en config (paramètre inopérant ici) | BE: `GlobalExceptionHandler.java`, `application.yml` | Les formulaires frontend (React Hook Form + zod) ne pourront pas afficher d'erreurs de validation champ par champ renvoyées par le serveur | **Corriger le backend** (amélioration, priorité basse) : inclure la liste des champs invalides dans `error.message` ou un champ dédié. Non bloquant pour une v1 d'intégration si la validation côté client (zod) reste la ligne de défense principale. |

### Enums / statuts

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 5.1 | Enums `DocumentTypeStatus`, `GeneratedDocumentStatus`, `Tone`, `Language`, `TargetLength` existent côté backend mais ne sont utilisés par aucun contrôleur | Définis dans `docuai-api/.../dto/enums/*` avec valeurs françaises cohérentes avec le frontend (`IMPORTE, EN_EXTRACTION, BROUILLON, FORMEL, COURT`...) | BE: `dto/enums/*` (code orphelin) | Bonne nouvelle : le nommage des valeurs d'enum est **déjà aligné** avec le frontend, mais ces enums ne servent à rien tant que les contrôleurs Documents Types / Generations n'existent pas | Pas de correction de nommage à faire — juste brancher ces enums existants sur les futurs contrôleurs (§1.2 à 1.5). Vérifier `MessageRole` qui a un `@JsonProperty` mappant vers minuscules (`user`/`assistant`), cohérent avec le frontend. |
| 5.2 | `RoleName` frontend (`ADMIN`, `UTILISATEUR`) vs autorisations backend réelles | Le backend utilise des `@PreAuthorize("hasAuthority('MANAGE_USERS')")` (permissions fines), pas des noms de rôle directement | BE: `UserController`, `RoleController` · FE: `guard.tsx`, `hasRole()` | Le frontend vérifie des noms de rôle (`ADMIN`/`UTILISATEUR`) alors que le backend raisonne en permissions (`MANAGE_USERS`, `MANAGE_ROLES`) | À clarifier ensemble à l'étape 4 : soit le backend expose aussi le rôle nommé dans le JWT/claims pour que le frontend s'aligne simplement, soit le frontend migre vers une logique de permissions. Le JWT actuel ne contient d'ailleurs aucun claim `roles`/`permissions` (voir §Authentification 6.2) — à corriger dans tous les cas. |

### Authentification

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 6.1 | Token mock non-JWT vs vrai JWT HS256 | Frontend génère `mock-access-{userId}-{timestamp}` ; backend émet un vrai JWT HS256 avec `sub, iat, exp` uniquement | FE: `lib/api/client.ts:91-92` · BE: `JwtTokenProvider` | Attendu, à corriger par nature du passage mock→réel | **Corriger le frontend** : remplacer par le vrai flux (stocker le JWT tel que reçu, ne plus le fabriquer). |
| 6.2 | Aucun claim `roles`/`permissions` dans le JWT | Le frontend stocke `user.roles` (avec permissions imbriquées) depuis la session, mais rien n'interdit une désync si le token expire et qu'on ne revalide que le token sans re-fetch l'utilisateur | BE: `JwtTokenProvider` (claims = `sub, iat, exp` seuls) | Fonctionnel pour l'instant car `JwtResponse` renvoie `roles`/`permissions` séparément au login/refresh — mais fragile pour tout refresh silencieux futur | Non bloquant immédiat. À surveiller à l'étape 4.4 lors de l'implémentation du refresh automatique. |
| 6.3 | Secret JWT et TTL configurés dans un namespace jamais lu par le code | `application.yml` définit `docuai.jwt.*` (clés RSA, issuer) ; `JwtTokenProvider` lit `jwt.secret`/`jwt.expiration.*` (namespace différent) → tombe systématiquement sur les valeurs par défaut codées en dur, y compris un **secret HS256 en clair dans le code source** | BE: `JwtTokenProvider.java` vs `application.yml` | Risque de sécurité (secret par défaut prévisible, TTL non configurables par environnement) ; en local ça "fonctionne" mais silencieusement mal configuré | **Corriger le backend** : soit faire lire `JwtTokenProvider` depuis `docuai.jwt.*` (et migrer vers RSA comme le suggère la config existante), soit renommer les clés `application.yml` vers `jwt.*` pour correspondre au code. Le secret ne doit plus être en dur. Je recommande d'aligner sur `application.yml` (RSA) car c'est la convention déjà documentée dans `.env.example`. |
| 6.4 | Pas d'endpoint `/logout`, pas de révocation | Frontend n'appelle aucune déconnexion serveur (déconnexion = suppression locale du store Zustand uniquement) ; backend n'a pas de blacklist Redis malgré Redis déployé | FE: pas de fonction logout serveur trouvée · BE: commentaire "pourrait vérifier une blacklist Redis" non implémenté | Cahier des charges §4.4 exige révocation en liste noire Redis | **Corriger le backend** : implémenter `/api/v1/auth/logout` + blacklist Redis des refresh tokens. **Corriger le frontend** : appeler cet endpoint à la déconnexion. |
| 6.5 | Pas de refresh automatique côté frontend | `refreshSession()` existe mais n'est jamais invoquée automatiquement (pas d'intercepteur 401) | FE: `lib/auth-store.ts` | Actuellement sans impact (mock), deviendra un problème réel dès connexion au vrai JWT (expiration 15 min) | **Corriger le frontend** : ajouter un intercepteur Axios gérant le 401 → tentative de refresh silencieux → redirection login si échec (prévu à l'étape 4.4). |
| 6.6 | Contrôle de rôle admin incomplet côté frontend ET absent côté backend sur certaines routes sensibles | `admin/users`, `admin/ai-models`, `admin/audit` : aucun `hasRole('ADMIN')` côté frontend ; côté backend, `ActivityLogController` (`/api/v1/admin/logs`) n'a **aucun** `@PreAuthorize` malgré son chemin `/admin/` ; `NotificationController` ne vérifie pas que `userId` correspond à l'utilisateur connecté (IDOR potentiel) | FE: 3 pages admin · BE: `ActivityLogController`, `NotificationController` | Faille de contrôle d'accès réelle, pas seulement un écart de contrat | **Corriger le backend en priorité** (le frontend n'est jamais une barrière de sécurité fiable) : ajouter `@PreAuthorize` sur `ActivityLogController`, vérifier la propriété de la ressource dans `NotificationController`. **Corriger aussi le frontend** pour l'UX (masquer les menus / rediriger avant l'appel serveur). |

### Streaming

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 7.1 | Aucun endpoint de streaming côté backend | Frontend attend un flux d'événements `{type: 'progress'|'section'|'done', sectionIndex, total, sectionLabel, content}` | FE: `lib/api/generator.ts` · BE: absent | Fonctionnalité cœur de métier non implémentable en l'état | Développement backend complet requis : `SseEmitter` sur `/api/v1/generations/{id}/stream`, en conservant si possible le contrat d'événements déjà défini côté frontend pour limiter la réécriture de `chat/page.tsx`. `EventSource` natif ne supporte pas de headers d'auth custom — prévoir soit un token en query param signé courte durée, soit `@microsoft/fetch-event-source` côté frontend (à trancher à l'étape 4.5). |

### Upload de fichiers

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 8.1 | Le frontend n'envoie jamais de binaire (ni `FormData`) | Import Document Type et documents de référence : seul le nom de fichier est transmis au mock | FE: `documents-types/import/page.tsx`, `chat/page.tsx` | Aucun contenu réel n'atteindrait le backend même une fois branché | **Corriger le frontend** : implémenter un vrai `FormData` avec le champ nommé `file` (aligné sur `@RequestParam("file")` du backend). |
| 8.2 | Limite de taille affichée (10 Mo) incohérente avec le cahier des charges (25 Mo) et avec le backend réel (25 Mo) | Frontend : texte statique "10 Mo max", non appliqué en JS · Backend : `spring.servlet.multipart.max-file-size: 25MB` (conforme au cahier des charges) | FE: `documents-types/import/page.tsx:124` | Message trompeur pour l'utilisateur, aucune validation client réelle | **Corriger le frontend** : afficher 25 Mo et ajouter une validation JS réelle sur `file.size` avant envoi. |
| 8.3 | Pas de validation MIME réelle côté backend malgré une config déclarée | `docuai.upload.allowed-mime-types` dans `application.yml` n'est lu par aucune classe ; n'importe quel type de fichier est accepté et passé à Tika | BE: `DocumentController`, `DocumentParserService` | Risque (upload de fichiers arbitraires), non conforme à l'intention du cahier des charges | **Corriger le backend** : brancher la validation MIME sur la config existante avant extraction Tika. |
| 8.4 | Endpoint d'upload existant (`/api/v1/documents/upload`) mais pas de notion de "Document Type" associée | Le endpoint retourne juste le texte extrait, sans créer d'entité Document Type/Structure | BE: `DocumentController` | Écart fonctionnel : il manque la couche métier au-dessus de l'extraction brute | Lié à 1.2 — développement backend requis pour relier extraction → création de Document Type → structuration. |

### CORS

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 9.1 | Origines autorisées en dur, `PATCH` absent des méthodes CORS | `http://localhost:3000` et `:5173` en dur dans `SecurityConfig` (pas de lecture depuis `application.yml`/env) ; méthodes autorisées `GET,POST,PUT,DELETE,OPTIONS` — **`PATCH` manquant** | BE: `SecurityConfig.corsConfigurationSource()` | Le frontend Next.js tourne bien sur `:3000` (donc origine déjà correcte), mais **toute requête `PATCH`** (utilisée massivement par le frontend : `PATCH /api/v1/users/{id}`, `.../document-types/{id}`, `.../generations/{id}`, `.../ai-configs/{id}`, `.../notifications/{id}`) sera bloquée par le préflight CORS une fois ces routes implémentées | **Corriger le backend** : ajouter `PATCH` à la liste des méthodes autorisées ; idéalement externaliser les origines autorisées dans `application.yml`/variable d'env plutôt qu'en dur (facilite la prod plus tard, non bloquant pour le local). |

### Pagination / filtrage

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 10.1 | Aucune pagination serveur nulle part | `ApiSuccess.meta` (frontend) supporte `total/page/pageSize` mais n'est utilisé par aucune fonction mock ; côté backend, `ApiResponse.meta` existe mais n'est jamais rempli ; `admin/logs` retourne un `top 10` fixe sans paramètre | FE: `types/index.ts` (`ApiSuccess.meta`) · BE: `ApiResponse.java`, `ActivityLogController` | Les listes (utilisateurs, audit logs, notifications) ne passeront pas à l'échelle, mais **non bloquant pour la démo locale** vu les volumes de données de test | Hors périmètre strict d'intégration (déjà absent des deux côtés, donc pas un écart d'intégration à proprement parler). À signaler comme limitation connue dans le README, pas à corriger dans cette mission sauf demande explicite. |

### Types de données

| # | Écart | Détail | Fichiers | Impact | Correction proposée |
|---|---|---|---|---|---|
| 11.1 | Format des identifiants incohérent selon les entités | Frontend : `id: string` partout, sans format imposé, fixtures avec IDs courts non-UUID (`u1`, `dt1`...) · Backend : `Utilisateur.idUtilisateur` en `UUID`, mais `Notification` et `NotificationController` utilisent un `Long` | FE: `types/index.ts` · BE: `Utilisateur.java` (UUID), `Notification`/`NotificationController` (Long) | Incohérence même **au sein du backend** (UUID pour users, Long pour notifications) | **Corriger le backend** pour la cohérence interne (uniformiser sur UUID, conforme au cahier des charges "UUID en string") — le frontend n'a pas à changer puisqu'il utilise déjà `string` générique compatible avec les deux formats sérialisés en JSON. |
| 11.2 | Dates | Frontend : ISO 8601 string partout (`toISOString()`) · Backend : `LocalDateTime` (ex. `Utilisateur.dateCreation`, `AiResponse.generatedAt`) sérialisé par Jackson par défaut en tableau `[2026,7,29,10,30,0]` sauf configuration `jackson.serialization.write-dates-as-timestamps: false` avec module `jsr310` | BE: aucune config Jackson trouvée pour forcer le format ISO string sur `LocalDateTime` | **Potentiellement bloquant** : si Jackson n'est pas configuré pour sérialiser `LocalDateTime` en ISO-8601 string, le frontend recevra un tableau de nombres au lieu d'une chaîne de date — à vérifier empiriquement au démarrage du backend (étape 5) | **Corriger le backend** si confirmé : ajouter `spring.jackson.serialization.write-dates-as-timestamps: false` (ou équivalent explicite) dans `application.yml`, et s'assurer que le module `jackson-datatype-jsr310` est bien sur le classpath (généralement inclus par défaut avec Spring Boot Starter Web récent, à vérifier). |

---

## 2. Synthèse des décisions proposées

| Type de correction | Nombre d'écarts | Détail |
|---|---|---|
| **Développement backend requis** (fonctionnalité absente, hors simple alignement) | 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.12, 7.1 | Catégories, Documents Types, conversations, générations, streaming, config IA, dashboard, CRUD users complet, changement de mot de passe |
| **Corriger le backend** (contrat existant mais divergent du cahier des charges / incohérent en interne) | 2.1, 2.2, 2.3, 3.1, 4.1, 6.3, 6.4, 6.6, 8.3, 9.1, 11.1, (11.2 à confirmer) | Nommage DTO users/roles, enveloppe systématique, format erreur JWT filter, secret JWT, logout+blacklist, contrôle d'accès admin/logs et notifications, validation MIME, CORS PATCH, UUID cohérent |
| **Corriger le frontend** | 1.10, 1.11, 1.13, 1.14, 6.1, 6.2, 6.5, 8.1, 8.2 | Aligner notifications/audit-logs sur les routes réelles, brancher export réel, implémenter upload réel, remplacer token mock par JWT réel, ajouter intercepteur refresh, corriger limite de taille affichée |
| **Signalé, non corrigé dans cette mission** | 10.1 (pagination), 4.2 (détail validation) | Limitations connues à documenter dans le README |

---

## 3. Question avant de poursuivre

Compte tenu du constat §0 — la majorité des écrans (Documents Types, conversations, générations, streaming, config IA, dashboard, CRUD users) n'ont **aucun** backend à connecter, pas seulement un contrat différent — je veux votre arbitrage avant de lancer l'étape 4.

Trois options possibles, non exclusives :
1. Je corrige d'abord tout ce qui est un vrai "écart de contrat" entre code existant des deux côtés (auth, users lecture, roles, upload/extraction, export, notifications, logs) pour obtenir une intégration réelle et propre sur ce périmètre réduit mais fonctionnel — et je documente clairement le reste comme hors périmètre.
2. En plus du point 1, j'implémente aussi les endpoints backend manquants les plus critiques pour le parcours utilisateur demandé à l'étape 5 (Documents Types, conversations, générations + streaming) — c'est un travail de développement backend significatif, pas juste de l'intégration.
3. Je fais 1, puis on revoit ensemble la priorisation du reste (catégories, dashboard, config IA) dans une itération séparée.

Je recommande l'option 3 : elle donne rapidement une base connectée et fiable (auth + users + upload/export + notifications), sans vous engager tout de suite sur l'ampleur de développement de l'option 2. Dites-moi comment vous voulez trancher, et je poursuis l'étape 4 sur cette base.
