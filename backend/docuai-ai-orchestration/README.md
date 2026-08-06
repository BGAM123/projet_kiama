# docuai-ai-orchestration — Bloc 5

Infrastructure IA : Pattern Stratégie multi-fournisseurs (`AiProviderPort`),
Prompt Builder, pipeline RAG (embeddings + recherche pgvector), Structural
Validator, Content Assembler.

Ce module ne définit **aucun endpoint REST** — il est consommé par
`docuai-api` (Bloc 6, `/conversations` et `/generations`) via
`GenerationOrchestrator`. Rien n'est branché sur un contrôleur pour l'instant.

---

## Arborescence

```
com.docuai.ai
├── port/            AiProviderPort (generate / streamGenerate / provider)
├── enums/           AiProvider (OPENAI, CLAUDE, GEMINI, MISTRAL, OLLAMA, DEEPSEEK, GROQ, QWEN)
├── dto/              GenerationRequest, GenerationResult, ChatMessage, Chunk (delta de streaming)
├── provider/        OpenAiAdapter, ClaudeAdapter, OllamaAdapter, GeminiAdapter, MistralAdapter,
│                    DeepSeekAdapter, GroqAdapter, QwenAdapter (tous réels, @Component)
│                    AbstractOpenAiStyleAdapter (base commune OpenAI/Mistral/DeepSeek/Groq/Qwen)
├── service/         AiProviderFactory, GenerationOrchestrator, PromptBuilder,
│                    StructuralValidator, ContentAssembler
├── rag/             ChunkingService, EmbeddingService, SimilaritySearchService
├── config/          AiProperties (docuai.ai.*), AiClientConfig (WebClient.Builder partagé)
└── exception/       AiProviderException (-> 503 AI_PROVIDER_UNAVAILABLE, GlobalExceptionHandler)
```

## Fournisseurs

| Fournisseur | API | Streaming |
|---|---|---|
| OpenAI | Chat Completions (`/chat/completions`) | SSE |
| Claude (Anthropic) | Messages API (`/messages`) | SSE (`content_block_delta`) |
| Ollama (local, pas de clé API) | `/api/chat` | NDJSON |
| Gemini | Generative Language API | SSE (`alt=sse`) |
| Mistral | Chat Completions (compatible OpenAI) | SSE |
| DeepSeek | Chat Completions (compatible OpenAI) | SSE |
| Groq | Chat Completions (compatible OpenAI) | SSE |
| Qwen (Alibaba Cloud DashScope) | Chat Completions (compatible OpenAI) | SSE |

Tous sont des beans Spring (`@Component`) — `AiProviderFactory` les découvre
automatiquement (injection de `List<AiProviderPort>`), donc
`isAvailable(provider)` vaut désormais `true` pour les huit. Un fournisseur
sans clé API configurée (`docuai.ai.<fournisseur>.api-key`) reste un bean
valide mais échoue à l'appel (`requireApiKey()`, message explicite) — la
disponibilité du bean et la présence d'une vraie clé sont deux choses
différentes. `GenerationOrchestrator` s'appuie en plus sur
`ai_model_config.actif`/`est_defaut` (Bloc 8, `/api/v1/ai-configs`) pour
choisir *lequel* utiliser par défaut.

## Ajouter/brancher un nouveau fournisseur (Pattern Stratégie)

1. Créer (ou décommenter) une classe `XxxAdapter implements AiProviderPort`
   (ou héritant de `AbstractOpenAiStyleAdapter` si l'API est compatible
   Chat Completions OpenAI) dans `provider/`.
2. Ajouter `@Component` — auto-découverte par `AiProviderFactory`
   (injection de `List<AiProviderPort>`).
3. Fournir la clé API via `docuai.ai.<fournisseur>.api-key`
   (`application.yml` / variable d'environnement, cf. `.env.example`).
4. Si le fournisseur n'est pas déjà dans l'énum `AiProvider` (`OPENAI/CLAUDE/
   GEMINI/MISTRAL/OLLAMA/DEEPSEEK/GROQ/QWEN`), ajouter la valeur à la fois à
   l'énum et à la contrainte `chk_ai_fournisseur` de `ai_model_config`
   (nouvelle migration Flyway — voir V7__activate_gemini_deepseek_qwen.sql
   pour le patron exact : contrainte + seed `ai_model_config`).
5. Le rendre sélectionnable comme fournisseur par défaut via
   `/api/v1/ai-configs` (Bloc 8).

## RAG (pgvector)

- `ChunkingService` découpe le texte brut d'un document de référence en
  fragments de ~1000 caractères (chevauchement 150) — pas de découpage
  sémantique pour cette itération.
- `EmbeddingService` calcule les embeddings via OpenAI
  `text-embedding-3-small` (1536 dimensions, fournisseur unique volontaire :
  mélanger des embeddings de modèles différents dans le même espace
  vectoriel produirait des similarités incohérentes). Nécessite
  `OPENAI_API_KEY` même si le fournisseur de génération par défaut est un
  autre fournisseur.
- `SimilaritySearchService` interroge `document_chunk` via l'opérateur
  pgvector `<=>` (distance cosinus) parmi les chunks des documents de
  référence d'une conversation.
- `DocumentChunk` (`docuai-core`) mappe la colonne `embedding
  vector(1536)` via `PgVectorType`, un `UserType` Hibernate 6 manuel
  (pas de support natif pgvector dans Hibernate, contrairement à JSONB) —
  lit/écrit la représentation texte pgvector directement via `PGobject`,
  sans dépendre de l'enregistrement du type `com.pgvector.PGvector` sur la
  connexion JDBC.

## Résilience

`resilience4j.retry.instances.ai-provider` et
`resilience4j.circuitbreaker.instances.ai-provider` sont déjà configurés
(`application.yml`) mais **pas encore branchés** (pas d'annotation
`@Retry`/`@CircuitBreaker`) — même choix que
`docuai-api/service/DocumentTypeExtractionService` au Bloc 4 : éviter une
annotation potentiellement silencieusement inopérante (proxy Spring AOP sur
une méthode héritée d'une classe abstraite, non vérifiable sans build
local) plutôt que de la poser sans certitude qu'elle s'applique. À rebrancher
explicitement (et vérifier via un test d'intégration réel) lors d'une
itération ultérieure.

## Non couvert par ce Bloc (attendu aux blocs suivants)

- Aucun contrôleur REST (`/conversations`, `/generations`, streaming SSE
  HTTP côté client) — Bloc 6.
- CRUD `/api/v1/ai-configs` — Bloc 8 (l'entité `AiModelConfig` et son
  repository existent déjà, côté lecture seule pour `GenerationOrchestrator`).
- Indexation effective des documents de référence importés (relier
  `ChunkingService`/`EmbeddingService` à l'upload d'un `DocumentReference`)
  — Bloc 6, une fois l'entité introduite.
- Index approximatif `ivfflat` sur `document_chunk.embedding` — laissé en
  commentaire dans V1 (à créer après un premier chargement réel de données).

## Vérification

Comme pour les blocs précédents, **aucune compilation n'a été possible dans
cet environnement de développement** (pas d'accès à `mvn`/`javac`/Docker).
Avant de considérer ce bloc comme définitivement validé :

```bash
mvn -pl docuai-ai-orchestration,docuai-core -am clean package
```

et, avec de vraies clés API renseignées dans `.env`, un test manuel via un
petit test unitaire/`main` appelant `GenerationOrchestrator#generate` (aucun
endpoint HTTP n'existe encore pour le tester via curl/Postman — ce sera le
Bloc 6).
