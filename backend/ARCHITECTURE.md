# DocuAI Backend — Architecture (proposition, Bloc 1)

Conformément à la consigne du prompt maître (« Propose d'abord l'arborescence
complète du projet... avant de générer le code »), voici l'arborescence
retenue avant de poursuivre avec les Blocs 2 à 9.

## Build : Maven multi-module

Maven plutôt que Gradle — justification (voir aussi le commentaire dans
`pom.xml`) : `spring-boot-starter-parent` fournit un BOM de versions cohérent
particulièrement adapté à un multi-module, le format XML explicite se relit
facilement sans exécuter de build (contrainte réelle de l'environnement de
développement actuel, sans accès à un outil de build), et l'écosystème
Spring/Java d'entreprise est très majoritairement outillé pour Maven.

## Modules (section 4.2 du prompt maître)

```
docuai-parent/                          (pom.xml — packaging "pom", BOM interne)
│
├── docuai-core/                        DOMAINE + adaptateurs JPA
│   └── com.docuai.core
│       ├── model/                      Entités JPA (Utilisateur, Role, Permission,
│       │                               Categorie, DocumentType, DocumentStructure,
│       │                               ExtractionJob, Conversation, Message,
│       │                               DocumentReference, DocumentGenere,
│       │                               DocumentChunk, AiModelConfig, ActivityLog,
│       │                               Notification)
│       ├── repository/                 Ports Spring Data JPA (une interface par entité)
│       └── service/                    Règles métier pures (ex. transitions de statut)
│
├── docuai-security/                    INFRASTRUCTURE sécurité
│   └── com.docuai.security
│       ├── config/                     SecurityConfig (filtres, CORS, RBAC method-security)
│       ├── jwt/                        JwtTokenProvider (RS256), JwtAuthenticationFilter,
│       │                               JwtAuthenticationEntryPoint, TokenBlacklistService (Redis)
│       └── service/                    UserDetailsServiceImpl, UserDetailsImpl
│
├── docuai-extraction-client/           INFRASTRUCTURE extraction documentaire
│   └── com.docuai.extraction
│       ├── config/                     UploadProperties, MinioConfig, ClamAvProperties
│       ├── service/                    DocumentParserService (Tika/POI/PDFBox),
│       │                               StorageService (MinIO), ExtractionFacade,
│       │                               StructureBuilderService (arbre JSON),
│       │                               ExtractionJobService (suivi asynchrone)
│       ├── dto/                        ExtractedContentDetail, StructureNode
│       └── exception/                  UnsupportedFileTypeException, FileTooLargeException
│
├── docuai-ai-orchestration/            INFRASTRUCTURE IA (Pattern Stratégie)
│   └── com.docuai.ai
│       ├── port/                       AiProviderPort (generate / streamGenerate)
│       ├── provider/                   OpenAiAdapter, ClaudeAdapter, OllamaAdapter (réels),
│       │                               GeminiAdapter, MistralAdapter, DeepSeekAdapter
│       │                               (structurés, non branchés par défaut)
│       ├── service/                    AiProviderFactory, GenerationOrchestrator,
│       │                               PromptBuilder, StructuralValidator, ContentAssembler
│       ├── rag/                        EmbeddingService, ChunkingService, SimilaritySearchService
│       ├── dto/                        GenerationRequest, GenerationResult, Chunk
│       └── enums/                      AiProvider
│
├── docuai-export/                      INFRASTRUCTURE export
│   └── com.docuai.export
│       ├── service/                    ExportFacade
│       ├── strategy/                   DocxGeneratorStrategy, PdfGeneratorStrategy,
│       │                               MarkdownGeneratorStrategy (+ interface commune)
│       ├── dto/                        ExportRequest
│       └── enums/                      ExportFormat
│
└── docuai-api/                         PRÉSENTATION + APPLICATION + bootstrap
    └── com.docuai.api
        ├── DocuAiApplication.java      point d'entrée Spring Boot
        ├── controller/                 auth, user, role, category, documenttype,
        │                               conversation, generation, aiconfig, dashboard,
        │                               admin (audit), notification
        ├── service/                    Services applicatifs (orchestration/transactions,
        │                               un par domaine ci-dessus)
        ├── dto/                        DTO d'API (jamais d'entité JPA exposée directement)
        ├── mapper/                     Mappers MapStruct (Entité ↔ DTO)
        ├── exception/                  GlobalExceptionHandler (@ControllerAdvice), erreurs métier
        ├── config/                     ApiResponseWrapperAdvice, OpenApiConfig, AsyncConfig
        └── resources/
            └── db/migration/           V1__init_schema.sql, V2__seed_roles_permissions.sql, ...
```

## Couches (section 4.1, « hexagonale simplifiée »)

| Couche | Où | Contenu |
|---|---|---|
| Présentation | `docuai-api/.../controller` | Contrôleurs REST fins, validation Bean Validation, aucune logique métier |
| Application | `docuai-api/.../service` | Orchestration métier, transactions, coordination des ports |
| Domaine | `docuai-core/.../model`, `.../repository` (interfaces) | Entités, règles métier, ports de persistance |
| Infrastructure | `docuai-core` (adaptateurs JPA), `docuai-security`, `docuai-extraction-client`, `docuai-ai-orchestration`, `docuai-export` | Implémentations techniques des ports (JPA, JWT, MinIO, appels IA HTTP, génération de fichiers) |

Entorse pragmatique assumée (comme le permet le prompt maître) : les
adaptateurs JPA (implémentations Spring Data des ports de persistance) vivent
dans `docuai-core` aux côtés des entités et des interfaces de repository,
plutôt que dans un module infrastructure séparé — Spring Data JPA génère ces
adaptateurs à partir des interfaces, la séparation physique en module
apporterait peu de valeur ici.

## Pattern Stratégie IA (section 4.3)

Contrat repris à l'identique :

```java
public interface AiProviderPort {
    GenerationResult generate(GenerationRequest request);
    Flux<Chunk> streamGenerate(GenerationRequest request);
}
```

`AiProviderFactory` résout l'implémentation par `AiProvider` (enum), lue
depuis `ai_model_config.fournisseur` (le défaut) ou un choix explicite. Voir
la justification "abstraction manuelle plutôt que Spring AI" dans
`docuai-ai-orchestration/pom.xml`.

## Ce que ce Bloc 1 livre concrètement

- `pom.xml` (parent + 6 modules), dépendances déclarées mais **aucune classe
  métier encore** (seul `DocuAiApplication` existe, pour vérifier que le
  multi-module compile et démarre).
- `docker-compose.yml` + `.env.example` + `Dockerfile` (Postgres+pgvector,
  Redis, MinIO, Mailhog, ClamAV en profil optionnel, backend).
- `V1__init_schema.sql` (schéma complet, y compris les tables RAG/async non
  détaillées dans le schéma indicatif du prompt mais explicitement demandées)
  et `V2__seed_roles_permissions.sql` (rôles, permissions, comptes de test,
  catégories, un Document Type d'exemple avec structure, configs IA de base).
- `application.yml` (profils dev/prod, sans le namespace JWT — ajouté au Bloc 2).

## Prochaines étapes (attendent validation)

Bloc 2 — Sécurité : entités `Utilisateur`/`Role`/`Permission`, JWT RS256,
RBAC, endpoints `/auth/*`, `/users`, `/roles`. Bloc 3 — Référentiels :
entités `Categorie`/`DocumentType`/`DocumentStructure`, endpoints
`/categories`, `/document-types` (+ `.../structure`). Puis Blocs 4 à 9 dans
l'ordre de la section 11 du prompt maître.
