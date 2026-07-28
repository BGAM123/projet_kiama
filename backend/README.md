# DocuAI — Backend

API REST Spring Boot 3 / Java 21 pour l'extraction de structure de documents Word/PDF
et la génération de nouveaux documents via une orchestration IA multi-fournisseurs.

Ce dépôt contient **uniquement le backend** (aucun frontend V1). Toutes les
fonctionnalités sont vérifiables via Swagger, Postman ou `curl`.

---

## État d'avancement (par blocs, section 11 du cahier)

- [x] **Bloc 1 — Socle** : structure Maven multi-module, `docker-compose.yml`,
      migrations Flyway `V1` (schéma) et `V2` (rôles / permissions / compte admin).
- [x] **Bloc 2 — Sécurité** : JWT RS256, RBAC, endpoints `/auth`, `/users`, `/roles`.
- [x] **Bloc 3 — Référentiels** : Catégories, Documents Types (CRUD, versionnement).
- [x] **Bloc 4 — Extraction** : upload, MIME, parsers DOCX/PDF, arbre JSON.
- [x] **Bloc 5 — IA** : adaptateurs Stratégie, Prompt Builder, RAG pgvector, validator.
- [x] **Bloc 6 — Génération** : conversations, orchestration, streaming SSE.
- [x] **Bloc 7 — Export** : DOCX / PDF / Markdown.
- [x] **Bloc 8 — Transverses** : dashboard, notifications, audit, config IA.
- [ ] **Bloc 9 — Finalisation** : durcissement, tests, Postman, docs.

---

## Arborescence des modules

```
docuai-parent/                     (pom.xml)
├── docuai-core/                   entités JPA, DTO, ports, règles métier
├── docuai-security/               JWT RS256, RBAC, filtres Spring Security
├── docuai-extraction-client/      Tika, Apache POI (DOCX), PDFBox (PDF)
├── docuai-ai-orchestration/       adaptateurs multi-fournisseurs, Prompt Builder,
│                                  RAG pgvector, Structural Validator, Assembler
├── docuai-export/                 exporteurs DOCX / PDF / Markdown
└── docuai-api/                    contrôleurs REST, main app, migrations Flyway
    └── src/main/resources/db/migration/
        ├── V1__init_schema.sql
        └── V2__seed_roles_permissions.sql
```

---

## Lancer en local

**Prérequis** : Docker Desktop (≥ 4.x), Docker Compose v2.

```bash
cp .env.example .env
docker compose up -d postgres redis minio minio-init
```

À ce stade du Bloc 1, le service `backend` peut être bâti mais n'expose encore
aucun endpoint (Bloc 2 apporte l'authentification). Pour uniquement vérifier
que l'infrastructure est saine, arrête-toi ici.

### Vérifier le socle

```bash
docker compose ps
```

Les 3 conteneurs (`postgres`, `redis`, `minio`) doivent être `healthy`, et
`minio-init` doit être `Exited (0)`.

### Vérifier que les migrations Flyway s'appliquent

Les migrations tourneront automatiquement au premier démarrage du backend
(Bloc 2). Pour les inspecter dès maintenant :

```bash
docker exec -it docuai-postgres psql -U docuai -d docuai -c "\dt"
docker exec -it docuai-postgres psql -U docuai -d docuai -c "SELECT nom FROM role;"
docker exec -it docuai-postgres psql -U docuai -d docuai -c "SELECT code FROM permission;"
```

*(Les tables n'existent qu'après le premier démarrage du backend qui déclenche
Flyway ; ou tu peux appliquer les fichiers `V1`/`V2` manuellement.)*

### Console MinIO

- URL : http://localhost:9001
- Login / mdp : valeurs de `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (`.env`)
- Buckets créés automatiquement par `minio-init` : `docuai-sources`,
  `docuai-references`, `docuai-exports`.

---

## Compte administrateur de bootstrap

La migration `V2` crée un compte :

- **email** : `admin@docuai.local`
- **mot de passe** : `ChangeMe!2026` (⚠️ à rotationner dès la production)
- rôle : `ADMIN` (toutes permissions)

L'endpoint `/api/v1/auth/login` arrivera au **Bloc 2**.

---

## Ajouter un nouveau fournisseur IA (Pattern Stratégie)

À implémenter au **Bloc 5**. Principe :

1. Créer une classe `XxxAdapter implements AiProviderPort` dans
   `docuai-ai-orchestration`.
2. L'enregistrer dans `AiProviderFactory` (auto-découverte Spring via `@Component`).
3. Ajouter le fournisseur à la contrainte `chk_ai_fournisseur` de
   `ai_model_config` (nouvelle migration Flyway `V3__add_provider_xxx.sql`).
4. Le rendre configurable via `/api/v1/ai-configs` (Bloc 8).

---

## Variables d'environnement clés

Voir `.env.example`. Les plus sensibles :

| Variable | Description |
|---|---|
| `POSTGRES_PASSWORD` | mot de passe Postgres (dev par défaut) |
| `MINIO_ROOT_PASSWORD` | mot de passe MinIO |
| `DOCUAI_JWT_PRIVATE_KEY` / `DOCUAI_JWT_PUBLIC_KEY` | paire RSA PEM inline pour JWT RS256 (Bloc 2) |
| `DOCUAI_ADMIN_BOOTSTRAP_PASSWORD` | mot de passe initial du compte admin |
| `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OLLAMA_BASE_URL`, ... | clés fournisseurs IA (Bloc 5) |
