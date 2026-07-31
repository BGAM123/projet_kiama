# DocuAI — Backend

API REST Spring Boot 3 / Java 21 pour l'extraction de structure de documents Word/PDF
et la génération de nouveaux documents via une orchestration IA multi-fournisseurs.

Reconstruction complète démarrée le 2026-07-30 à partir du prompt maître
(`../1785408291591_PROMPT_DocuAI_Backend_Uniquement.md` fourni par l'utilisateur).
Voir `ARCHITECTURE.md` pour le détail de l'arborescence proposée.

---

## État d'avancement

- [x] **Bloc 1 — Socle** : structure Maven multi-module, `docker-compose.yml`,
      `Dockerfile`, migrations Flyway `V1` (schéma complet) et `V2` (seed
      rôles/permissions/comptes/catégories/Document Type d'exemple/configs IA).
- [x] **Bloc 2 — Sécurité** : entités `Utilisateur`/`Role`/`Permission`, JWT
      **RS256** (`PemKeyReader` + `JwtKeyConfig`), `TokenBlacklistService`
      (Redis, révocation + rotation du refresh token), RBAC par
      `@PreAuthorize`, `/auth/login`, `/auth/refresh`, `/auth/logout`,
      `/auth/password`, `/users` (CRUD complet + reset mot de passe admin),
      `/roles` (CRUD, suppression bloquée si le rôle est encore assigné).
      Mappers MapStruct (`UserMapper`, `RoleMapper`, `PermissionMapper`).
      **En attente de validation avant de poursuivre le Bloc 3.**
- [x] **E-mail de bienvenue** (anticipé sur le Bloc 8) : `POST /users` publie
      un `UserCreatedEvent` après le `save()` (mot de passe en clair jamais
      persisté ni renvoyé) ; `UserWelcomeEmailListener` l'écoute en
      `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`
      (`AsyncConfig`) pour envoyer l'e-mail via `MailService`
      (`JavaMailSender`) sans jamais bloquer ni faire échouer la requête HTTP,
      même si Mailhog/le relais SMTP est indisponible (erreur simplement
      loguée). Voir `docuai.mail.from` / `docuai.app.frontend-url` dans
      `application.yml`.
- [x] **Bloc 3 — Référentiels** : entités `Categorie`/`DocumentType`/
      `DocumentStructure` (+ `StructureNode`, mappé nativement en JSONB via
      Hibernate 6 `@JdbcTypeCode(SqlTypes.JSON)`). `/categories` : CRUD complet
      (lecture ouverte à tout utilisateur authentifié, mutations réservées à
      `CATEGORY_MANAGE`), suppression bloquée si une catégorie est encore
      utilisée (`CATEGORY_IN_USE`, même pattern que `ROLE_IN_USE`).
      `/document-types` : lecture (`DOCUMENT_TYPE_READ`, filtres optionnels
      `categoryId`/`status`), modification nom/description/catégorie et
      archivage logique (`DOCUMENT_TYPE_MANAGE`) ; `GET`/`PUT
      .../{id}/structure` pour consulter/corriger l'arbre extrait.
- [x] **Bloc 4 — Extraction documentaire** : `POST /document-types/import`
      (multipart, `DOCUMENT_TYPE_IMPORT`) fait tout en un appel — upload,
      extraction de texte brut (Tika, `TextExtractionService`), construction
      de l'arbre de structure (`StructureExtractionService`) et stockage du
      fichier source dans MinIO (bucket `docuai.minio.bucket-sources`, clé
      persistée dans `document_type.fichier_source_cle`) — puis crée le
      `DocumentType` (statut `STRUCTURE_EXTRAITE`, ou `ECHEC_EXTRACTION` si la
      structure n'a pas pu être construite ; le fichier reste importé dans ce
      cas). `POST /document-types/{id}/extract` (`DOCUMENT_TYPE_EXTRACT`)
      retélécharge le fichier déjà stocké et relance l'extraction sans
      réimporter. `POST /document-types/{id}/validate`
      (`DOCUMENT_TYPE_MANAGE`, sur `DocumentTypeService`) active le Document
      Type (`STRUCTURE_EXTRAITE`/`EN_VALIDATION` -> `ACTIF`, 409 sinon).
      Seul le **.docx** bénéficie d'une vraie détection structurelle (Apache
      POI : titres via le style de paragraphe "Heading N"/"Titre N",
      tableaux, ordre réel du document). Le **PDF** (PDFBox n'expose aucune
      sémantique de titre native), le **texte brut/Markdown** (titres `#`
      réels pour Markdown uniquement) et le repli **.doc legacy** (non
      couvert par `poi-ooxml`, qui ne lit que l'OOXML — repli sur le texte
      Tika) sont découpés en paragraphes plats, sans hiérarchie. L'arbre
      produit est plat (headings/paragraphes/tableaux en frères, comme
      l'exemple seedé en V2) ; l'éditeur manuel côté frontend permet
      d'imbriquer des sous-sections après coup. ClamAV (`docuai.clamav.*`)
      reste désactivé par défaut — non branché. Pas de retry/circuit-breaker
      malgré `resilience4j.retry.instances.extraction` déjà configuré (le
      brancher proprement demande d'extraire l'appel dans un collaborateur
      dédié pour passer par le proxy Spring AOP).
      **En attente de validation avant de poursuivre le Bloc 5.**
- [x] **Bloc 5 — IA** : `AiProviderPort` (Pattern Stratégie, `generate`/
      `streamGenerate`) implémenté par `OpenAiAdapter`/`ClaudeAdapter`/
      `OllamaAdapter` (réels, appels HTTP via `WebClient`, streaming SSE pour
      OpenAI/Claude et NDJSON pour Ollama) et `GeminiAdapter`/`MistralAdapter`/
      `DeepSeekAdapter` (structurés, non branchés par défaut — pas de
      `@Component`, voir `docuai-ai-orchestration/README.md`).
      `AiProviderFactory` auto-découvre les adaptateurs Spring ;
      `GenerationOrchestrator` résout la configuration à utiliser (choix
      explicite ou fournisseur par défaut, `ai_model_config.est_defaut`,
      nouvelles entités `AiModelConfig`/`DocumentChunk` dans `docuai-core`) et
      délègue à l'adaptateur résolu. `PromptBuilder` construit le prompt
      système (structure attendue du Document Type, langue/ton/longueur
      cible) et le prompt utilisateur (contexte RAG) ; `StructuralValidator`
      vérifie a posteriori que les titres Markdown générés respectent la
      structure attendue ; `ContentAssembler` recompose les sections en un
      contenu unique. RAG : `ChunkingService` (découpage par caractères avec
      chevauchement), `EmbeddingService` (OpenAI `text-embedding-3-small`,
      1536 dimensions), `SimilaritySearchService` (recherche pgvector par
      distance cosinus, opérateur `<=>`) — `DocumentChunk.embedding` est
      mappé via `PgVectorType`, un `UserType` Hibernate 6 manuel (pas de
      support natif pgvector dans Hibernate). Aucune migration Flyway
      nécessaire (`ai_model_config`/`document_chunk` existaient déjà dans
      `V1__init_schema.sql`, seedés en `V2`). Aucun endpoint REST ajouté —
      ce bloc est uniquement l'infrastructure IA, consommée par le Bloc 6.
      `resilience4j.retry`/`circuitbreaker` "ai-provider" restent configurés
      mais non branchés (même choix assumé qu'au Bloc 4, voir le README du
      module). **En attente de validation avant de poursuivre le Bloc 6.**
- [x] **Bloc 6 — Génération** : nouvelles entités `Conversation`/`Message`/
      `DocumentReference`/`DocumentGenere` (`docuai-core`, + énums
      `MessageRole`/`Language`/`Tone`/`TargetLength`/`DocumentGenereStatut`,
      `GenerationSectionNode` pour le JSONB `document_genere.sections`) ;
      aucune migration Flyway nécessaire (tables déjà présentes dans V1).
      `POST/GET /api/v1/conversations`, `GET/POST /api/v1/conversations/{id}/messages`
      (`CONVERSATION_USE`) — l'envoi d'un message appelle réellement
      `GenerationOrchestrator` (Bloc 5) pour la réponse assistant, avec repli
      sur un message générique si aucun fournisseur IA n'est disponible.
      `GET/POST /api/v1/conversations/{id}/reference-documents` (multipart) :
      upload MinIO (bucket `docuai.minio.bucket-references`) + indexation RAG
      (`ChunkingService`/`EmbeddingService`/`DocumentChunkRepository`) —
      dégradée sans faire échouer l'import si l'indexation échoue (même
      principe que l'extraction de structure au Bloc 4), plafonnée par
      `docuai.generation.max-reference-documents`.
      `POST /api/v1/generations` (`DOCUMENT_GENERATE`) crée le document généré
      (statut `EN_GENERATION`, sections initialisées `PENDING` depuis la
      structure du Document Type, hors nœud `cover`) ; `GET
      /api/v1/generations/{id}/stream` (SSE, même contrat d'événements que la
      simulation déjà stabilisée côté frontend — `progress`/`section`/`done`)
      déroule la génération section par section sur un pool dédié
      (`generationExecutor`, `AsyncConfig`) : `PromptBuilder` construit les
      prompts (structure attendue + RAG des documents de référence via
      `SimilaritySearchService`), chaque section retente jusqu'à
      `docuai.generation.section-retry-attempts` fois en cas d'échec IA
      (`AiProviderException`), le contenu est assemblé et persisté au fur et
      à mesure, `StructuralValidator` vérifie a posteriori (non bloquant) que
      les titres attendus sont présents. Statut final `GENERE` ou `ECHEC`
      (échec partiel toléré : les sections en échec sont marquées `FAILED`
      sans interrompre les suivantes). `GET /api/v1/generations?userId=` et
      `GET /api/v1/generations/{id}` (`HISTORY_READ_OWN`/`HISTORY_READ_ALL`,
      propriété vérifiée en plus de la permission) ; `PATCH
      /api/v1/generations/{id}` (`DOCUMENT_EDIT_OWN`, propriétaire uniquement)
      pour l'édition manuelle du contenu (statut -> `EN_EDITION`).
      **En attente de validation avant de poursuivre le Bloc 7.**
- [ ] Bloc 7 — Export (DOCX/PDF/Markdown)
- [ ] Bloc 8 — Transverses (dashboard, notifications, journal d'activité, `/ai-configs`)
- [ ] Bloc 9 — Finalisation (durcissement OWASP, tests, Postman/.http, doc Swagger)

---

## Lancer l'infrastructure (ce qui est vérifiable dès ce Bloc 1)

```bash
cd backend
cp .env.example .env
```

Générer une paire de clés RS256 (obligatoire dès le Bloc 2 pour que le backend
démarre) :

```bash
openssl genpkey -algorithm RSA -out jwt_private.pem -pkeyopt rsa_keygen_bits:2048
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
```

⚠️ Utiliser `genpkey` (format PKCS#8, `-----BEGIN PRIVATE KEY-----`) et non
`genrsa` (format PKCS#1, `-----BEGIN RSA PRIVATE KEY-----`) : `PemKeyReader`
(`docuai-security`) attend du PKCS#8, lisible nativement par
`java.security.KeyFactory` sans dépendance supplémentaire (pas de Bouncy
Castle). Un fichier `genrsa` échouera au démarrage avec une erreur explicite.

Coller le contenu des deux fichiers `.pem` dans `.env` (`DOCUAI_JWT_PRIVATE_KEY`
/ `DOCUAI_JWT_PUBLIC_KEY`), avec des `\n` littéraux à la place des retours à
la ligne.

```bash
docker compose up -d postgres redis minio minio-init mailhog
docker compose ps
```

Les conteneurs `postgres`, `redis`, `minio` doivent être `healthy`, et
`minio-init` `Exited (0)`.

### Vérifier le socle + les migrations

```bash
docker compose up -d --build backend
docker compose logs backend | grep -i flyway
```

Puis :

```bash
docker exec -it docuai-postgres psql -U docuai -d docuai -c "\dt"
docker exec -it docuai-postgres psql -U docuai -d docuai -c "SELECT nom FROM role;"
docker exec -it docuai-postgres psql -U docuai -d docuai -c "SELECT code FROM permission ORDER BY code;"
docker exec -it docuai-postgres psql -U docuai -d docuai -c "SELECT nom, statut FROM document_type;"
curl -s http://localhost:8080/actuator/health
```

`/actuator/health` doit répondre `{"status":"UP"}` si le socle est sain.

### Tester le Bloc 2 (sécurité)

```bash
# 1. Connexion admin — récupère accessToken/refreshToken
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@docuai.local","password":"ChangeMe!2026"}' | tee /tmp/login.json

ACCESS=$(jq -r '.data.accessToken' /tmp/login.json)
REFRESH=$(jq -r '.data.refreshToken' /tmp/login.json)

# 2. Route protégée avec le jeton d'accès
curl -s http://localhost:8080/api/v1/users -H "Authorization: Bearer $ACCESS"

# 3. Sans jeton -> 401 uniforme { "error": { "code": "UNAUTHORIZED", ... } }
curl -s http://localhost:8080/api/v1/users

# 4. Connexion utilisateur non-admin -> 403 attendu sur /users (RBAC)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"utilisateur@docuai.local","password":"ChangeMe!2026"}' | tee /tmp/login-user.json
USER_ACCESS=$(jq -r '.data.accessToken' /tmp/login-user.json)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/users -H "Authorization: Bearer $USER_ACCESS"
# -> 403

# 5. Rafraîchissement (rotation : REFRESH devient invalide après cet appel)
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"

# 6. Déconnexion puis réutilisation du refresh token -> doit échouer (liste noire)
curl -s -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"
curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" -d "{\"refreshToken\":\"$REFRESH\"}"
# -> error INVALID_REFRESH_TOKEN

# 7. Changement de mot de passe (soi-même, authentifié)
curl -s -X POST http://localhost:8080/api/v1/auth/password \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"currentPassword":"ChangeMe!2026","newPassword":"NouveauMdp!2026"}'
```

Ou via Swagger UI (http://localhost:8080/swagger-ui.html) : `POST /auth/login`,
copier `data.accessToken`, cliquer "Authorize" en haut à droite, coller le
jeton, puis appeler `GET /users`.

### Tester le Bloc 3 (référentiels)

```bash
# Catégories — lecture ouverte à tout authentifié, mutations réservées ADMIN
curl -s http://localhost:8080/api/v1/categories -H "Authorization: Bearer $ACCESS"
curl -s -X POST http://localhost:8080/api/v1/categories \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"name":"Juridique","description":"Contrats et avenants"}'

# Documents Types — le Document Type seedé en V2 (id fixe, pratique pour tester)
DT_ID=00000000-0000-0000-0000-000000000001
curl -s http://localhost:8080/api/v1/document-types -H "Authorization: Bearer $ACCESS"
curl -s http://localhost:8080/api/v1/document-types?status=ACTIF -H "Authorization: Bearer $ACCESS"
curl -s http://localhost:8080/api/v1/document-types/$DT_ID/structure -H "Authorization: Bearer $ACCESS"

# Utilisateur non-admin : DOCUMENT_TYPE_READ ok, DOCUMENT_TYPE_MANAGE refusé (403)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/document-types \
  -H "Authorization: Bearer $USER_ACCESS"
curl -s -o /dev/null -w "%{http_code}\n" -X PATCH http://localhost:8080/api/v1/document-types/$DT_ID \
  -H "Authorization: Bearer $USER_ACCESS" -H "Content-Type: application/json" -d '{"name":"x"}'
# -> 403
```

### Tester le Bloc 4 (extraction)

```bash
# Import d'un fichier .docx — multipart, categoryId réel (ex. celui listé par
# GET /categories), crée le Document Type + extrait sa structure en un appel
CATEGORY_ID=$(curl -s http://localhost:8080/api/v1/categories -H "Authorization: Bearer $ACCESS" | jq -r '.data[0].id')
curl -s -X POST http://localhost:8080/api/v1/document-types/import \
  -H "Authorization: Bearer $ACCESS" \
  -F "file=@/chemin/vers/exemple.docx" \
  -F "name=Exemple importé" \
  -F "description=Import de test" \
  -F "categoryId=$CATEGORY_ID" | tee /tmp/import.json

NEW_DT_ID=$(jq -r '.data.id' /tmp/import.json)
jq '.data.status' /tmp/import.json   # -> "STRUCTURE_EXTRAITE" (ou "ECHEC_EXTRACTION")

# Structure extraite (titres/paragraphes/tableaux détectés depuis le .docx)
curl -s http://localhost:8080/api/v1/document-types/$NEW_DT_ID/structure -H "Authorization: Bearer $ACCESS"

# Activation — refusée tant que le statut n'est pas STRUCTURE_EXTRAITE/EN_VALIDATION
curl -s -X POST http://localhost:8080/api/v1/document-types/$NEW_DT_ID/validate -H "Authorization: Bearer $ACCESS"

# Relancer l'extraction à partir du fichier déjà stocké (sans réimporter)
curl -s -X POST http://localhost:8080/api/v1/document-types/$NEW_DT_ID/extract -H "Authorization: Bearer $ACCESS"

# Endpoint générique d'extraction seule (sans créer de Document Type)
curl -s -X POST http://localhost:8080/api/v1/documents/upload \
  -H "Authorization: Bearer $ACCESS" -F "file=@/chemin/vers/exemple.pdf"
```

### Tester le Bloc 6 (génération)

Nécessite au moins une configuration IA active (`ai_model_config.est_defaut`,
seedée en V2 avec OpenAI comme fournisseur par défaut) — sans clé API réelle
renseignée dans `.env`, `POST /generations/{id}/stream` se termine en statut
`ECHEC` (message explicite `AI_PROVIDER_UNAVAILABLE`, pas un 500).

```bash
USER_ID=$(curl -s http://localhost:8080/api/v1/users -H "Authorization: Bearer $ACCESS" | jq -r '.data[0].id')

# Conversation — DT_ID = un Document Type déjà ACTIF (ex. celui du Bloc 4)
curl -s -X POST http://localhost:8080/api/v1/conversations \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d "{\"userId\":\"$USER_ID\",\"documentTypeId\":\"$NEW_DT_ID\",\"title\":\"Test Bloc 6\"}" | tee /tmp/conv.json
CONV_ID=$(jq -r '.data.id' /tmp/conv.json)

# Chat — appelle réellement le fournisseur IA par défaut
curl -s -X POST http://localhost:8080/api/v1/conversations/$CONV_ID/messages \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"content":"Bonjour, peux-tu me résumer ce que ce document doit contenir ?"}'
curl -s http://localhost:8080/api/v1/conversations/$CONV_ID/messages -H "Authorization: Bearer $ACCESS"

# Document de référence (indexé pour le RAG — dégradé si OPENAI_API_KEY absente)
curl -s -X POST http://localhost:8080/api/v1/conversations/$CONV_ID/reference-documents \
  -H "Authorization: Bearer $ACCESS" -F "file=@/chemin/vers/reference.pdf"

# Démarrer une génération — sections initialisées depuis la structure du Document Type
curl -s -X POST http://localhost:8080/api/v1/generations \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d "{\"conversationId\":\"$CONV_ID\",\"documentTypeId\":\"$NEW_DT_ID\",\"userId\":\"$USER_ID\",\"language\":\"FR\",\"tone\":\"NEUTRE\",\"targetLength\":\"MOYEN\",\"contentPivot\":\"Contrat de prestation pour un client B2B.\"}" \
  | tee /tmp/gen.json
GEN_ID=$(jq -r '.data.id' /tmp/gen.json)

# Suivi en direct (SSE) — un événement JSON par ligne {"type":"progress"|"section"|"done", ...}
curl -N -s http://localhost:8080/api/v1/generations/$GEN_ID/stream -H "Authorization: Bearer $ACCESS"

# Relecture / édition manuelle
curl -s http://localhost:8080/api/v1/generations/$GEN_ID -H "Authorization: Bearer $ACCESS"
curl -s -X PATCH http://localhost:8080/api/v1/generations/$GEN_ID \
  -H "Authorization: Bearer $ACCESS" -H "Content-Type: application/json" \
  -d '{"content":"Contenu corrigé manuellement."}'

# Utilisateur non-admin sur la génération d'un autre utilisateur -> 403
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/generations/$GEN_ID \
  -H "Authorization: Bearer $USER_ACCESS"
```

### Console MinIO

- URL : http://localhost:9001
- Login / mot de passe : `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (`.env`)
- Buckets créés automatiquement par `minio-init` : `docuai-sources`, `docuai-references`, `docuai-exports`.

### Mailhog (e-mails transactionnels)

- Interface web : http://localhost:8025
- L'e-mail de bienvenue envoyé à la création d'un utilisateur (`POST /users`)
  y est visible immédiatement en dev.

---

## Comptes de test (migration V2)

| Email | Mot de passe | Rôle |
|---|---|---|
| `admin@docuai.local` | `ChangeMe!2026` | ADMIN (toutes permissions) |
| `utilisateur@docuai.local` | `ChangeMe!2026` | UTILISATEUR |

⚠️ À changer impérativement avant tout environnement partagé.

---

## Build local (nécessite Java 21 + Maven — non fournis dans ce dépôt)

```bash
mvn -pl docuai-api -am clean package
mvn -pl docuai-api -am spring-boot:run
```

**Important** : les fichiers de ce Bloc 1 n'ont pas pu être compilés dans
l'environnement ayant produit ces changements (pas d'accès à Maven Central, ni
à `mvn`/`javac`, ni à Docker depuis ce bac à sable — réseau restreint). Lancez
`mvn -pl docuai-api -am clean package` (ou `docker compose build backend`) en
local avant de considérer ce socle comme définitivement validé, en particulier
pour confirmer que Flyway applique `V1`/`V2` sans erreur sur un Postgres réel.

---

## Ajouter un nouveau fournisseur IA (Bloc 5, Pattern Stratégie)

1. Créer une classe `XxxAdapter implements AiProviderPort` dans `docuai-ai-orchestration`.
2. L'enregistrer comme `@Component` (auto-découverte par `AiProviderFactory`).
3. Ajouter la valeur à la contrainte `chk_ai_fournisseur` de `ai_model_config`
   (nouvelle migration Flyway, ex. `V5__add_provider_xxx.sql`) si le fournisseur
   n'est pas déjà dans la liste `OPENAI/CLAUDE/GEMINI/MISTRAL/OLLAMA/DEEPSEEK`.
4. Le rendre configurable via `/api/v1/ai-configs` (Bloc 8).
