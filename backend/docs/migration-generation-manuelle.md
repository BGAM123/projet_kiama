# Note de migration — passage à la génération manuelle assistée

## Pourquoi ce changement

Le chatbot ne pilote plus la production des documents. La génération devient
manuelle, section par section : l'utilisateur rédige lui-même chaque section
d'un document, et l'IA n'intervient plus que comme assistant de reformulation
à la demande (bouton "Améliorer avec l'IA", jamais appliqué automatiquement).

Seul le module Document Type continue de produire du contenu par IA — mais
uniquement sous forme de squelette structurel (titres/sous-titres/tableaux),
jamais de texte rédigé — et ce flux **coexiste** avec l'import de fichier
existant plutôt que de le remplacer.

## Ce qui a été retiré

- **Endpoints** : `POST /api/v1/generations`, `GET /api/v1/generations`,
  `GET /api/v1/generations/{id}`, `PATCH /api/v1/generations/{id}`,
  `GET /api/v1/generations/{id}/stream` (SSE). Remplacés par `/api/v1/documents/**`
  (voir plus bas).
- **Code backend** : `GenerationController`, `GenerationService`,
  `GenerationStreamService`, `GenerationLockService` (verrou Redis de
  génération concurrente), `DocumentGenere`/`DocumentGenereStatut`/
  `GenerationSectionNode` (entités), `DocumentGenereRepository`,
  `GenerationMapper`/`GenerationSectionMapper`, DTOs associés.
- **Table** `document_genere` (DROP, migration V9 — aucune donnée de
  production à préserver à ce stade du projet).
- **Frontend** : page `/chat` (conversation + génération SSE en direct),
  page `/editor/[id]` (édition Tiptap du document entièrement généré),
  `lib/api/generator.ts` (client SSE).

## Ce qui a été conservé tel quel

Le module **Conversation** (chat, messages, documents de référence + RAG :
`Conversation`, `Message`, `DocumentReference`, `DocumentChunk`, leurs
tables, repositories, service et endpoints `/api/v1/conversations/**`) reste
en base et dans le code, **mais n'est plus utilisé par aucune page
frontend**. Il est dormant, pas supprimé — conservé comme fondation possible
pour enrichir plus tard le bouton "Améliorer avec l'IA" avec du contexte RAG
(documents de référence de l'utilisateur), sans qu'il soit nécessaire de le
réécrire pour cette itération.

## Ce qui a été ajouté

### Document Type (squelette par IA)
- `POST /api/v1/document-types/generate` — description en langage naturel →
  squelette (titres/sous-titres/tableaux). Coexiste avec
  `POST /api/v1/document-types/import` (fichier).
- `document_structure.source` (`IMPORTED` | `AI_GENERATED`, migration V8).
- Le format de l'arbre (`document_structure.arbre_json` / `StructureNode`)
  n'a **pas** changé de modèle (toujours JSONB imbriqué, `heading`+`level`
  pour la hiérarchie) — seulement étendu (`paragraph_placeholder`,
  `tableColumns`, `suggestedRowCount`) pour rester compatible avec
  l'extraction déterministe de fichier et l'éditeur de structure existants.

### Document (édition manuelle assistée)
- Nouvelles tables relationnelles (migration V9) : `document`,
  `document_section` (type propre : `TITLE|SUBTITLE|SUB_SUBTITLE|TABLE|
  PARAGRAPH_PLACEHOLDER`, distinct du vocabulaire JSONB du Document Type),
  `document_section_history`.
- Endpoints `POST /api/v1/documents`, `GET /api/v1/documents`,
  `GET /api/v1/documents/{id}`, `PUT .../sections/{sectionId}`,
  `POST .../sections/{sectionId}/improve`,
  `POST .../sections/{sectionId}/apply-suggestion`,
  `POST .../sections/{sectionId}/reject-suggestion`,
  `POST /api/v1/documents/{id}/finalize`,
  `GET /api/v1/documents/{id}/export?format=DOCX|PDF|MARKDOWN`.
- **Export** : `finalize` dépose une archive DOCX sur MinIO (lien de
  téléchargement persistant, `exportUrl`), tandis que `GET .../export`
  produit le fichier à la demande dans le format choisi — le contenu y est
  réassemblé depuis les sections à chaque appel, donc toujours à jour même
  après une édition post-finalisation. Réponse binaire brute (hors enveloppe
  `ApiResponse`), même mécanique que `POST /api/v1/export` (Bloc 7), qui
  reste disponible pour un export ad hoc titre+contenu.
- Aucune nouvelle permission : réutilise `DOCUMENT_GENERATE`,
  `DOCUMENT_EDIT_OWN`, `HISTORY_READ_OWN`/`HISTORY_READ_ALL`,
  `DOCUMENT_EXPORT` (déjà seedées).
- Logs structurés JSON (`logback-spring.xml`, `net.logstash.logback` —
  dépendance déjà présente, jusque-là jamais câblée) avec `traceId`
  (MDC, `TraceIdFilter`) par requête, et un événement
  `suggestion_outcome=GENERATED|APPLIED|REJECTED` par section pour mesurer
  l'usage réel du bouton d'amélioration IA.
- Frontend : `/documents/new` (sélection du Document Type),
  `/documents/[id]` (plan des sections + éditeur par section + comparateur
  avant/après IA), remplace `/chat` dans la navigation ("Nouveau document").

### Éditeur de squelette (itération suivante)

Reprise de la maquette de l'interface de génération (plan + éditeur de
sections + score de confiance), adaptée à la stack plutôt que transposée
telle quelle.

- **Score de confiance** (`document_section.confidence_score`, migration
  V10) : le prompt d'amélioration demande désormais un objet JSON
  `{"content", "confidence"}`, lu par `SectionImprovementResponseParser`
  (docuai-ai-orchestration). Le score est **auto-déclaré par le modèle**, pas
  une probabilité calibrée : il sert à prioriser la relecture. Une réponse
  hors format n'est pas une erreur — le texte brut reste une suggestion
  valide, simplement sans score. Cycle de vie : posé à `improve`, conservé à
  `apply-suggestion` (il qualifie le texte devenu contenu retenu), effacé à
  `reject-suggestion` et à toute réécriture manuelle.
  `DocumentDTO.globalConfidenceScore` est la moyenne des seules sections
  évaluées (les autres ne comptent pas comme des zéros).
- **Export enrichi** : `MarkdownContentParser` reconnaît en plus les listes
  (à puces/numérotées, avec imbrication), les filets horizontaux, et découpe
  les marques inline (`**gras**`, `*italique*`, `` `code` ``, `[lien](url)`).
  `DocxDocumentExporter` écrit un run Word par marque, `PdfDocumentExporter`
  compose les lignes en changeant de police au fil du texte. Sans cela, la
  barre d'outils du nouvel éditeur aurait produit une mise en forme
  ressortant en caractères Markdown bruts dans les documents livrés.
- **Frontend** : `/documents/[id]` devient l'éditeur de squelette (plan
  numéroté avec pastilles de confiance et score global, sections dépliables,
  actions Accepter/Régénérer/Modifier, guide des seuils §3.4 repliable).
  L'éditeur riche (`components/rich-text-editor.tsx`) sérialise en Markdown
  via `lib/markdown-editor.ts` — le format réellement stocké et relu à
  l'export.
- **Limite assumée** : la maquette proposait police, taille, couleur,
  surlignage et alignement. Ces réglages n'ont aucune représentation en
  Markdown et seraient donc perdus à la sauvegarde comme à l'export ; ils ne
  sont pas repris. La barre d'outils n'expose que des mises en forme qui
  arrivent réellement dans le DOCX/PDF.

## Vérification effectuée

- Backend : `mvn test` → 59 tests verts sur tous les modules (dont
  `SectionImprovementResponseParserTest` et `MarkdownContentParserTest`,
  nouveaux, et les cas de cycle de vie du score de confiance dans
  `DocumentSectionServiceTest`/`DocumentServiceTest`).
- Frontend : `npm run typecheck` et `npm run build` propres ; la route
  `/documents/[id]` compile et se charge dans le serveur de dev.
- Le pont Markdown ↔ éditeur (`lib/markdown-editor.ts`) a été vérifié hors
  navigateur (titres, marques inline, listes imbriquées, tableaux, filets,
  échappement HTML, neutralisation des liens `javascript:`/`data:`).
- Sur le stack Docker réel (`docker compose up`) : V10 s'applique, la
  validation de schéma Hibernate passe, l'application démarre (0 redémarrage,
  `/actuator/health` → UP). **La première version de V10 déclarait
  `NUMERIC(5,2)` et faisait échouer le démarrage** (`ddl-auto=validate`
  attend `float(53)` pour un `Double`) — corrigé en `DOUBLE PRECISION`. Les
  tests unitaires, qui travaillent sur des mocks, ne pouvaient pas attraper
  cet écart : toute nouvelle colonne doit être validée contre un vrai
  Postgres avant d'être considérée comme terminée.
- **Non vérifié** : le rendu de l'éditeur avec de vraies données et l'export
  DOCX/PDF enrichi — la page est derrière l'authentification et aucun compte
  utilisable n'était disponible pendant la vérification.
- Un essai de démarrage complet contre une vraie base Postgres locale
  (`docker compose up postgres redis minio mailhog` + `mvn spring-boot:run`)
  a été tenté pour valider les migrations V8/V9 sur un moteur réel, mais
  bloqué par un conflit de port 5432 préexistant sur la machine de
  développement (un autre process y écoute déjà, sans rapport avec ce
  projet) — à relancer dans un environnement sans ce conflit pour la
  validation finale des migrations sur Postgres réel.
