# Rapport — Réécriture du backend DocuAI

Date : 2026-07-30
Périmètre : `backend` uniquement (Spring Boot multi-module Maven, stack technique inchangée). Le frontend n'a pas été modifié.
Base de départ : `rapport-ecarts-integration.md` (29/07/2026) + un travail d'intégration déjà en cours, non commité, qui corrigeait une partie des écarts « contrat » (voir §1).

---

## 0. Résumé

Objectif de la mission : que le backend expose réellement toutes les fonctionnalités attendues par le frontend (`frontend/lib/api/client.ts` et `types/index.ts`), en gardant Spring Boot 3 / Java 21 / Maven multi-module / Postgres+pgvector / Redis / MinIO — la stack déjà en place.

État en fin de mission :

- Les **8 domaines fonctionnels entièrement absents** identifiés par le rapport d'écarts (catégories, Documents Types, conversations, générations, streaming, config IA, dashboard, CRUD utilisateurs + mot de passe) sont **implémentés** : entités JPA, repositories, services, contrôleurs REST, sécurité par permission.
- Le point bloquant initial (classe `@SpringBootApplication` manquante) était déjà résolu par le travail en cours au moment où j'ai commencé.
- Docker était déjà en place (`Dockerfile` multi-stage + `docker-compose.yml` complet) et n'a pas eu besoin de modification structurelle.
- **Le build n'a pas pu être compilé ni exécuté dans cet environnement** : ni `mvn`, ni `javac` (JDK 21), ni `docker`, ni l'accès réseau à Maven Central/Docker Hub n'y sont disponibles (sandbox à réseau restreint). Le code a été relu statiquement (imports, cohérence des noms de champs entre entités/DTO/services, équilibre des accolades sur les 91 fichiers Java du module `docuai-api`/`docuai-core`) mais **doit être compilé en local avant mise en production** — voir §6.

---

## 1. Ce qui existait déjà avant cette session (non refait)

En prenant le projet en main, un travail d'intégration substantiel était déjà présent en local (non commité, 54 fichiers modifiés) et corrigeait une partie des écarts « contrat » du rapport initial :

| Écart du rapport d'origine | État constaté |
|---|---|
| 0.a — classe `DocuAiApplication` absente | **Déjà corrigée** |
| 2.1/2.2/2.3 — DTO users/roles en français, schémas incohérents | **Déjà corrigé** (`UserDTO`, `RoleDTO`, `UserMapper`) |
| 3.1 — enveloppe `ApiResponse` non systématique | **Déjà corrigé** (`ApiResponseWrapperAdvice`) |
| 4.1 — format d'erreur JWT filter incohérent | **Déjà corrigé** (`JwtAuthenticationEntryPoint`) |
| 6.3 — secret JWT en dur | **Déjà corrigé** (lecture depuis `docuai.jwt.secret`) |
| 6.4 — pas de logout/blacklist | **Déjà corrigé** (`TokenBlacklistService` + `/auth/logout`) |
| 6.6 — IDOR notifications / logs sans permission | **Déjà corrigé** |
| 8.3 — validation MIME non appliquée | **Déjà corrigée** |
| 9.1 — CORS sans `PATCH` | **Déjà corrigé** |
| 11.1 — UUID incohérent sur `Notification` | **Déjà corrigé** |

Ce travail n'a pas été retouché, seulement vérifié par lecture.

---

## 2. Ce qui a été ajouté dans cette session

### 2.1 Base de données (Flyway)

- `V3__generation_content_columns.sql` : `document_genere.contenu_pivot` retypé en `TEXT` (était `JSONB` alors que le frontend envoie une chaîne libre), `longueur_cible` retypé en `VARCHAR` (était `INT`, incompatible avec l'énum `TargetLength`), ajout de `contenu` (`TEXT`) et `sections` (`JSONB`).
- `V4__seed_ai_configs.sql` : jeu minimal de configurations IA (OpenAI, Ollama) pour que l'écran admin ne soit pas vide au premier démarrage.
- Aucune autre modification de schéma : les tables `categorie`, `document_type`, `document_structure`, `conversation`, `message`, `document_reference`, `document_genere`, `ai_model_config` existaient déjà dans `V1__init_schema.sql`, de même que toutes les permissions RBAC nécessaires dans `V2__seed_roles_permissions.sql` — je me suis appuyé dessus sans les modifier.

### 2.2 Nouveaux modules backend (entité → repository → service → contrôleur)

| Domaine | Entités JPA | Endpoints ajoutés | Permission(s) |
|---|---|---|---|
| **Catégories** | `Categorie` | `GET/POST/PATCH/DELETE /api/v1/categories` | lecture : authentifié ; écriture : `CATEGORY_MANAGE` |
| **Documents Types** | `DocumentType`, `DocumentStructure` | `GET/POST(import)/PATCH/DELETE /api/v1/document-types`, `GET/PUT /api/v1/document-types/{id}/structure` | `DOCUMENT_TYPE_READ`, `DOCUMENT_TYPE_MANAGE`, `DOCUMENT_TYPE_IMPORT` |
| **Conversations & messages** | `Conversation`, `Message`, `DocumentReference` | `GET/POST /api/v1/conversations`, `GET/POST /api/v1/conversations/{id}/messages`, `GET/POST /api/v1/conversations/{id}/reference-documents` | `CONVERSATION_USE` + vérification de propriété |
| **Générations** | `DocumentGenere` | `POST /api/v1/generations`, `GET /api/v1/generations?userId=`, `GET/PATCH /api/v1/generations/{id}`, `GET /api/v1/generations/{id}/stream` (SSE) | `DOCUMENT_GENERATE`, `DOCUMENT_EDIT_OWN`, `HISTORY_READ_OWN`/`HISTORY_READ_ALL` |
| **Config IA** | `AiModelConfig` | `GET/PATCH /api/v1/ai-configs` | `AI_CONFIG_MANAGE` |
| **Dashboard** | (lecture agrégée) | `GET /api/v1/dashboard/stats?userId=` | `DASHBOARD_READ_OWN`/`DASHBOARD_READ_ALL` |
| **Utilisateurs (CRUD complet)** | — (`Utilisateur` existant) | `POST/PATCH/DELETE /api/v1/users`, `PATCH /api/v1/users/{id}/password` | `USERS_MANAGE` |
| **Mot de passe personnel** | — | `POST /api/v1/auth/password` | authentifié (propriétaire uniquement) |

Toutes les permissions utilisées existaient déjà dans le seed RBAC (`V2`) — aucune permission n'a eu besoin d'être ajoutée.

### 2.3 Détails techniques notables

- **Streaming de génération (SSE)** : `GenerationController.stream()` renvoie un `SseEmitter` et reprend exactement le contrat d'événements déjà défini côté frontend (`lib/api/generator.ts` : `{type: 'progress'|'section'|'done', sectionIndex, total, sectionLabel, content}`). Chaque section appelle réellement `AiOrchestratorService` (même brique que `/api/v1/ai/generate` et le chat) — le contenu n'est pas simulé côté serveur. L'exécution se fait sur un pool dédié (`AsyncConfig.generationExecutor`) pour ne pas bloquer le thread de requête HTTP.
- **Import de Document Type** : relie enfin le pipeline d'extraction déjà réel (`ExtractionFacade` : Tika + upload MinIO) à un vrai cycle de vie métier. Après extraction, une structuration heuristique (`StructureExtractionService`) propose un arbre initial (titres détectés par longueur/casse de ligne) — un point de départ éditable, pas une analyse de mise en forme réelle (pas de styles Word/PDF disponibles depuis le texte brut Tika).
- **Chat IA** : `ConversationService.sendMessage` appelle réellement `AiOrchestratorService` (au lieu d'une réponse d'assistant statique côté serveur), avec repli sur un message générique si aucun fournisseur IA n'est configurable (pas de clé API en environnement local, par exemple).
- **Contrôle d'accès par propriété** : conversations, générations et dashboard vérifient que l'utilisateur authentifié est bien le propriétaire de la ressource demandée (même logique que la correction IDOR déjà appliquée aux notifications), avec une dérogation `HISTORY_READ_ALL`/`DASHBOARD_READ_ALL` pour les rôles qui les portent.
- **Sérialisation JSON dans Postgres** : les colonnes `jsonb` (`arbre_json`, `sections`, `versions_historique`) sont mappées via `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6, sans dépendance supplémentaire) plutôt qu'une bibliothèque tierce.
- **Dates Jackson** : `spring.jackson.serialization.write-dates-as-timestamps: false` ajouté à `application.yml` — sans ça, les `LocalDateTime` (dont les nouvelles entités) auraient été sérialisées en tableau `[année,mois,jour,...]` plutôt qu'en chaîne ISO-8601 attendue par le frontend (écart 11.2 du rapport d'origine, jusqu'ici non vérifié empiriquement).
- **`isDefault` sur `AiModelConfigDTO`** : écrit à la main plutôt qu'avec Lombok, car un champ booléen `isDefault` généré par Lombok produit un getter `isDefault()` que Jackson sérialise par défaut sous la clé `"default"` (il retire le préfixe `is`), pas `"isDefault"` comme attendu par le frontend — `@JsonProperty("isDefault")` explicite pour éviter ce piège classique.

---

## 3. Ce qui reste hors périmètre (limitations assumées)

- **Pas de pipeline RAG** : `document_chunk`/pgvector restent inutilisés — le chat et la génération n'exploitent pas encore les documents de référence par recherche de similarité, ils passent le contenu pivot brut au fournisseur IA.
- **Sélection du fournisseur par défaut non branchée** : `/api/v1/ai-configs` permet de désigner un fournisseur par défaut en base, mais `AiOrchestratorService` ne le lit pas encore (il retombe sur OpenAI puis le premier fournisseur disponible).
- **Structuration heuristique, pas une analyse de mise en forme réelle** — cf. §2.3.
- **Pagination serveur toujours absente** (`admin/logs` reste limité à 10 entrées) — signalé mais non traité, comme dans le rapport d'origine.
- **JWT toujours en HS256** (pas de migration RS256), claims sans rôles/permissions embarqués — inchangé par rapport à l'état précédent.

---

## 4. Docker

Le `Dockerfile` (multi-stage : build Maven puis image `eclipse-temurin:21-jre-alpine`) et `docker-compose.yml` (Postgres+pgvector, Redis, MinIO, mailhog, backend) existaient déjà et couvrent l'ensemble des nouveaux modules sans modification : les étapes `COPY docuai-core`, `COPY docuai-api`, etc. copient des répertoires entiers, donc les nouveaux fichiers Java/SQL sont inclus automatiquement. Aucun nouveau service externe n'a été introduit (pas de dépendance ajoutée en dehors de ce qui était déjà déclaré dans les `pom.xml`).

Pour lancer l'ensemble :

```bash
cd backend
cp .env.example .env
docker compose up -d --build
```

---

## 5. Fichiers modifiés/ajoutés (cette session)

- **Migrations** : `V3__generation_content_columns.sql`, `V4__seed_ai_configs.sql`
- **Entités** (`docuai-core/.../model`) : `Categorie`, `DocumentType`, `DocumentStructure`, `Conversation`, `Message`, `DocumentReference`, `DocumentGenere`, `AiModelConfig`
- **Repositories** (`docuai-core/.../repository`) : un par entité ci-dessus
- **Services** (`docuai-api/.../service`) : `CategoryService`, `DocumentTypeService`, `StructureExtractionService`, `ConversationService`, `GenerationService`, `AiConfigService`, `DashboardService`, `UserService`
- **Contrôleurs** (`docuai-api/.../controller`) : `CategoryController`, `DocumentTypeController`, `ConversationController`, `GenerationController`, `AiConfigController`, `DashboardController` ; `UserController` et `AuthController` complétés
- **DTO** (`docuai-api/.../dto`) : une quinzaine de nouvelles classes (une par ressource + requêtes de création/mise à jour)
- **Config** : `AsyncConfig` (pool SSE), `application.yml` (dates Jackson), `ApiResponseWrapperAdvice` (exclusion SSE), `SecurityConfig` (exclusion `/auth/password` du `permitAll`), `GlobalExceptionHandler` (`NotFoundException`, `IllegalArgumentException`)
- **Exception** : `NotFoundException`
- **Documentation** : `backend/README.md` mis à jour (état d'avancement, migrations, limitations)

---

## 6. Vérification et prochaines étapes

Cet environnement d'exécution n'a pas accès à Maven Central ni à Docker Hub (réseau restreint) et ne fournit qu'un JRE 11 sans `javac`/`mvn` — **aucune compilation n'a donc été possible ici**. La relecture a porté sur :

- la cohérence des noms de champs entre entités JPA, DTO et services (vérifiée fichier par fichier) ;
- l'équilibre des accolades/parenthèses sur les 91 fichiers Java des modules `docuai-api` et `docuai-core` (vérifié par script) ;
- la cohérence des imports inter-modules avec les `pom.xml` existants (aucune nouvelle dépendance Maven requise).

**Avant toute mise en service, il faut impérativement** :

1. `mvn -pl docuai-api -am clean package` (ou `docker compose build backend`) en local pour confirmer la compilation.
2. `docker compose up -d` et vérifier que Flyway applique `V3`/`V4` sans erreur (`docker compose logs backend | grep -i flyway`).
3. Un tour rapide de smoke tests sur les nouveaux endpoints (Swagger UI sur `http://localhost:8080/swagger-ui.html` une fois le backend démarré, avec le compte `admin@docuai.local` / `ChangeMe!2026`).
4. Côté frontend : `lib/api/client.ts` contient encore des fonctions mockées pour tous les domaines listés en §2.2 — les rebrancher sur `http.*` (comme cela a déjà été fait pour auth/users/notifications/export/upload) reste une étape distincte, non traitée dans cette mission backend-only.
