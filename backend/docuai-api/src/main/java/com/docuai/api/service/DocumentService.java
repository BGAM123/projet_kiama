package com.docuai.api.service;

import com.docuai.ai.dto.GeneratedSectionContent;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.SectionImprovement;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.service.DocumentContentResponseParser;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SectionImprovementResponseParser;
import com.docuai.api.dto.CreateDocumentRequest;
import com.docuai.api.dto.DocumentDTO;
import com.docuai.api.dto.GenerateDocumentRequest;
import com.docuai.api.dto.SectionSuggestionDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentMapper;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.DocumentSectionStatut;
import com.docuai.core.model.DocumentSectionType;
import com.docuai.core.model.DocumentStatut;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.DocumentReferenceRepository;
import com.docuai.core.repository.DocumentRepository;
import com.docuai.core.repository.DocumentSectionRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.export.ExportContent;
import com.docuai.export.ExportFormat;
import com.docuai.export.ExportService;
import com.docuai.export.ExportedFile;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.html.DocxHtmlImporter;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.api.util.FileNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cycle de vie d'un document en édition manuelle assistée : création (à
 * partir du squelette d'un Document Type actif, sections vides), lecture,
 * finalisation/export. L'édition du contenu de chaque section relève de
 * {@link DocumentSectionService} — ce service-ci ne touche pas à {@code
 * user_content}/{@code ai_suggested_content}.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    /** Même borne que {@code DocumentTypeGenerationService} : un JSON malformé déclenche une nouvelle tentative, jamais un contenu incomplet. */
    private static final int CONTENT_GENERATION_MAX_ATTEMPTS = 3;
    /** Taille de l'historique par utilisateur : au-delà, les documents les plus anciens sont purgés automatiquement (DB + stockage objet) — voir {@link #enforceHistoryLimit}. */
    private static final int MAX_HISTORY_DOCUMENTS = 20;

    private final DocumentRepository documentRepository;
    private final DocumentReferenceRepository documentReferenceRepository;
    private final DocumentSectionRepository documentSectionRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final DocumentMapper documentMapper;
    private final DocumentSectionMapper documentSectionMapper;
    private final DocumentSkeletonHtmlBuilder skeletonHtmlBuilder;
    private final DocumentContentHtmlBuilder documentContentHtmlBuilder;
    private final GenerationOrchestrator generationOrchestrator;
    private final PromptBuilder promptBuilder;
    private final SectionImprovementResponseParser sectionImprovementResponseParser;
    private final DocumentContentResponseParser documentContentResponseParser;
    private final FileIngestionService fileIngestionService;
    private final DocxHtmlImporter docxHtmlImporter;
    private final ExportService exportService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;
    private final DocumentReferenceContextService documentReferenceContextService;

    public DocumentService(DocumentRepository documentRepository,
                            DocumentReferenceRepository documentReferenceRepository,
                            DocumentSectionRepository documentSectionRepository,
                            DocumentTypeRepository documentTypeRepository,
                            DocumentStructureRepository documentStructureRepository,
                            DocumentMapper documentMapper,
                            DocumentSectionMapper documentSectionMapper,
                            DocumentSkeletonHtmlBuilder skeletonHtmlBuilder,
                            DocumentContentHtmlBuilder documentContentHtmlBuilder,
                            GenerationOrchestrator generationOrchestrator,
                            PromptBuilder promptBuilder,
                            SectionImprovementResponseParser sectionImprovementResponseParser,
                            DocumentContentResponseParser documentContentResponseParser,
                            FileIngestionService fileIngestionService,
                            DocxHtmlImporter docxHtmlImporter,
                            ExportService exportService,
                            ObjectStorageService objectStorageService,
                            MinioProperties minioProperties,
                            DocumentReferenceContextService documentReferenceContextService) {
        this.documentRepository = documentRepository;
        this.documentReferenceRepository = documentReferenceRepository;
        this.documentSectionRepository = documentSectionRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.documentMapper = documentMapper;
        this.documentSectionMapper = documentSectionMapper;
        this.skeletonHtmlBuilder = skeletonHtmlBuilder;
        this.documentContentHtmlBuilder = documentContentHtmlBuilder;
        this.generationOrchestrator = generationOrchestrator;
        this.promptBuilder = promptBuilder;
        this.sectionImprovementResponseParser = sectionImprovementResponseParser;
        this.documentContentResponseParser = documentContentResponseParser;
        this.fileIngestionService = fileIngestionService;
        this.docxHtmlImporter = docxHtmlImporter;
        this.exportService = exportService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
        this.documentReferenceContextService = documentReferenceContextService;
    }

    @Transactional
    public DocumentDTO create(CreateDocumentRequest request, Utilisateur currentUser) {
        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));
        DocumentStructure structure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                .orElseThrow(() -> new NotFoundException("STRUCTURE_NOT_FOUND", "Aucune structure trouvée pour ce Document Type."));

        Document document = Document.builder()
                .documentType(documentType)
                .utilisateur(currentUser)
                .statut(DocumentStatut.BROUILLON)
                // Le squelette est rendu d'emblée en HTML : c'est ce document
                // que l'éditeur type Word ouvre, et que l'utilisateur modifie
                // directement avant export.
                .contentHtml(skeletonHtmlBuilder.build(structure.getArbreJson()))
                .build();
        document = documentRepository.save(document);

        // Les sections restent créées : elles portent le plan validé du
        // Document Type (numérotation, colonnes attendues) et alimentent
        // l'amélioration IA par section, qui travaille toujours sur ce
        // découpage. Le contenu, lui, ne vit plus que dans contentHtml.
        List<DocumentSection> sections = new ArrayList<>();
        flatten(structure.getArbreJson(), document, null, sections);
        documentSectionRepository.saveAll(sections);
        enforceHistoryLimit(currentUser.getId());

        return toDto(document, sections);
    }

    /**
     * Crée un document directement depuis un fichier déposé par
     * l'utilisateur (.docx/.pdf/.md/.txt/.doc), sans passer par un Document
     * Type : c'est le second point d'entrée de la rédaction, à côté de {@link
     * #create}, pour l'utilisateur qui a déjà un document existant à
     * reprendre plutôt qu'un gabarit à remplir.
     * <p>
     * Seul le .docx bénéficie d'une vraie conversion avec mise en forme
     * ({@link DocxHtmlImporter} lit les runs Word : gras, couleur, police,
     * tableaux, images…). Les autres formats retombent sur le texte brut
     * extrait par Tika ({@link FileIngestionService}), reformé en paragraphes
     * — sans mise en forme à en tirer (PDF/texte n'en portent pas nativement
     * dans ce qu'on peut en extraire ici), mais entièrement éditable une fois
     * dans l'éditeur.
     */
    @Transactional
    public DocumentDTO importDocument(MultipartFile file, Utilisateur currentUser) {
        StoredFile stored = fileIngestionService.ingest(file);
        String extension = FileNames.extensionOf(stored.fileName());
        String contentHtml = "docx".equals(extension)
                ? docxHtmlImporter.toHtml(stored.content())
                : plainTextToHtml(stored.rawText());

        Document document = Document.builder()
                .utilisateur(currentUser)
                .statut(DocumentStatut.BROUILLON)
                .titre(titleFromFilename(file.getOriginalFilename()))
                .contentHtml(contentHtml)
                .build();
        document = documentRepository.save(document);
        enforceHistoryLimit(currentUser.getId());

        return toDto(document, List.of());
    }

    /**
     * Point d'entrée unique de la rédaction à partir d'un Document Type — a
     * remplacé {@link #create} (squelette vide), resté à côté de {@link
     * #importDocument} (fichier existant) : le plan d'un Document Type déjà
     * choisi, optionnellement rempli par l'IA à partir d'une description en
     * langage naturel de ce que l'utilisateur veut obtenir — même principe que
     * "Générer avec l'IA" pour un Document Type ({@code
     * DocumentTypeGenerationService}), mais ici le plan est déjà fixé : le LLM
     * ne produit que le contenu, jamais la structure.
     * <p>
     * Description vide/absente : l'IA n'est pas appelée du tout (pas d'appel
     * inutile), le document est créé en {@code BROUILLON} avec un squelette
     * vide — exactement le comportement de l'ancien {@link #create}. Sinon, le
     * document est créé même si la génération échoue après {@link
     * #CONTENT_GENERATION_MAX_ATTEMPTS} tentatives (JSON malformé, fournisseur
     * IA indisponible) — même dégradation vers un squelette vide (donc
     * {@code BROUILLON}), plutôt qu'une erreur. Une réponse partielle (le
     * modèle a oublié certaines sections) n'est pas non plus un échec : {@link
     * DocumentContentHtmlBuilder} retombe section par section sur le rendu
     * vide du squelette pour tout {@code id} absent de la réponse.
     */
    @Transactional
    public DocumentDTO generateContent(GenerateDocumentRequest request, Utilisateur currentUser) {
        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));
        DocumentStructure structure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                .orElseThrow(() -> new NotFoundException("STRUCTURE_NOT_FOUND", "Aucune structure trouvée pour ce Document Type."));
        List<StructureNode> tree = structure.getArbreJson();

        Document document = Document.builder()
                .documentType(documentType)
                .utilisateur(currentUser)
                .titre(request.getName())
                .build();

        boolean hasDescription = request.getDescription() != null && !request.getDescription().isBlank();
        Map<String, GeneratedSectionContent> generated = hasDescription
                ? generateSectionContents(documentType.getNom(), request.getDescription(), tree,
                        document.getLangue().name(), document.getTon().name())
                : Map.of();

        // Contenu réellement produit par l'IA -> SAUVEGARDE ; sans description
        // ou génération épuisée -> BROUILLON, comme un squelette vide créé
        // sans IA (cf. #create).
        document.setStatut(generated.isEmpty() ? DocumentStatut.BROUILLON : DocumentStatut.SAUVEGARDE);
        document.setContentHtml(documentContentHtmlBuilder.build(tree, generated));
        document = documentRepository.save(document);

        List<DocumentSection> sections = new ArrayList<>();
        flatten(tree, document, null, sections);
        documentSectionRepository.saveAll(sections);
        enforceHistoryLimit(currentUser.getId());

        return toDto(document, sections);
    }

    /**
     * Purge l'historique d'un utilisateur au-delà de {@link #MAX_HISTORY_DOCUMENTS}
     * documents (les plus récents conservés) — appelée après chaque création
     * ({@link #create}, {@link #importDocument}, {@link #generateContent}), pas
     * seulement à la lecture : l'historique ne doit jamais dépasser la limite,
     * même si l'utilisateur ne revisite jamais l'écran d'historique. Purge
     * réelle, pas un archivage : le document (sections, documents de référence,
     * chunks RAG — cascade DB, V9/V15) et ses fichiers MinIO (export, documents
     * de référence) sont définitivement supprimés.
     */
    private void enforceHistoryLimit(UUID userId) {
        List<Document> documents = documentRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId);
        if (documents.size() <= MAX_HISTORY_DOCUMENTS) {
            return;
        }
        for (Document toDelete : documents.subList(MAX_HISTORY_DOCUMENTS, documents.size())) {
            deleteDocumentAndStorage(toDelete);
        }
    }

    /** Supprime les fichiers MinIO liés (export + documents de référence) avant la ligne document elle-même (cascade DB pour sections/références/chunks). */
    private void deleteDocumentAndStorage(Document document) {
        for (DocumentReference reference : documentReferenceRepository.findByDocument_IdOrderByDateImportAsc(document.getId())) {
            try {
                objectStorageService.delete(minioProperties.getBucketReferences(), reference.getCheminStockage());
            } catch (Exception e) {
                log.warn("Suppression MinIO impossible pour le document de référence {} (document {} purgé de l'historique) : {}",
                        reference.getId(), document.getId(), e.getMessage());
            }
        }
        if (document.getMinioObjectKey() != null && !document.getMinioObjectKey().isBlank()) {
            try {
                objectStorageService.delete(minioProperties.getBucketExports(), document.getMinioObjectKey());
            } catch (Exception e) {
                log.warn("Suppression MinIO impossible pour l'export du document {} (purgé de l'historique) : {}",
                        document.getId(), e.getMessage());
            }
        }
        documentRepository.delete(document);
        log.info("history_prune documentId={} userId={}", document.getId(), document.getUtilisateur().getId());
    }

    /**
     * Boucle de tentatives calquée sur {@code DocumentTypeGenerationService#runGeneration}
     * (même borne, même logique) : un JSON malformé ou un fournisseur IA en
     * échec redémarre la génération depuis le même prompt, jusqu'à épuisement.
     * Contrairement au flux squelette, l'épuisement des tentatives n'empêche
     * pas la création du document — {@link #generateContent} retombe sur une
     * correspondance vide (squelette non rempli) plutôt que de faire échouer
     * toute la requête pour un problème de format côté modèle.
     */
    private Map<String, GeneratedSectionContent> generateSectionContents(
            String documentTypeName, String description, List<StructureNode> tree, String language, String tone) {
        String systemPrompt = promptBuilder.buildDocumentContentSystemPrompt(documentTypeName, description, tree, language, tone);

        for (int attempt = 1; attempt <= CONTENT_GENERATION_MAX_ATTEMPTS; attempt++) {
            try {
                GenerationRequest request = GenerationRequest.builder()
                        .systemPrompt(systemPrompt)
                        .userPrompt("Génère le contenu JSON pour ce document, en respectant strictement le schéma décrit.")
                        .build();
                String raw = generationOrchestrator.generate(request).getContent();
                List<GeneratedSectionContent> sections = documentContentResponseParser.parse(raw);
                return sections.stream()
                        .filter(section -> section.getId() != null)
                        // Un id dupliqué dans la réponse ne doit pas faire
                        // échouer la collecte : la première occurrence gagne.
                        .collect(Collectors.toMap(GeneratedSectionContent::getId, Function.identity(), (a, b) -> a));
            } catch (DocumentContentResponseParser.DocumentContentParseException | AiProviderException e) {
                log.warn("Échec de génération de contenu IA (tentative {}/{}) : {}",
                        attempt, CONTENT_GENERATION_MAX_ATTEMPTS, e.getMessage());
            }
        }
        log.warn("Génération de contenu IA épuisée après {} tentatives — document créé avec un squelette vide.",
                CONTENT_GENERATION_MAX_ATTEMPTS);
        return Map.of();
    }

    /** Un bloc vide entre deux lignes vides = un nouveau paragraphe — même découpage que {@code StructureExtractionService.extractPlainText}, mais en HTML plutôt qu'en nœuds de structure. */
    private String plainTextToHtml(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "<p></p>";
        }
        StringBuilder html = new StringBuilder();
        for (String block : rawText.split("\n\\s*\n")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            html.append("<p>").append(escapeHtml(trimmed).replace("\n", "<br>")).append("</p>");
        }
        return html.isEmpty() ? "<p></p>" : html.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Nom affichable dérivé du fichier déposé — contrairement à {@code FileNames.sanitize} (qui vise une clé d'objet MinIO sûre), les espaces et accents sont conservés : ce titre n'est jamais utilisé comme chemin de fichier. */
    private String titleFromFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        String title = (dot > 0 ? name.substring(0, dot) : name).strip();
        return title.isBlank() ? "Document importé" : title;
    }

    /**
     * Sauvegarde du document mis en forme dans l'éditeur (autosave debouncée
     * et clic explicite sur "Sauvegarder" partagent ce même point d'entrée).
     * Aucune transformation côté serveur : le HTML est stocké tel quel et
     * relu tel quel par l'éditeur, pour qu'un aller-retour ne dégrade jamais
     * la mise en forme. Il n'est interprété qu'au moment de l'export
     * ({@code HtmlContentParser}), qui n'exécute évidemment rien de son
     * contenu.
     * <p>
     * Premier enregistrement : le document quitte {@code BROUILLON} (squelette
     * tout juste créé, jamais touché) pour {@code SAUVEGARDE} — c'est ce statut
     * qui distingue dans l'Historique un document réellement travaillé d'un
     * squelette encore vierge. Un document déjà {@code FINALISE}/{@code
     * ARCHIVE} garde son statut : un enregistrement après coup n'annule pas
     * une finalisation ou un archivage déjà actés.
     */
    @Transactional
    public DocumentDTO saveContent(UUID id, String contentHtml, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        document.setContentHtml(contentHtml);
        if (document.getStatut() == DocumentStatut.BROUILLON) {
            document.setStatut(DocumentStatut.SAUVEGARDE);
        }
        document.setDateMaj(LocalDateTime.now());
        documentRepository.save(document);

        return toDto(document, documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id));
    }

    /**
     * Amélioration rédactionnelle d'un passage sélectionné dans l'éditeur.
     * Reprend le prompt et le parseur de l'amélioration par section
     * ({@link DocumentSectionService#improve}) : le découpage en sections n'est
     * plus le support de la rédaction, mais l'assistance IA reste la même —
     * une suggestion retournée, jamais appliquée d'office.
     */
    @Transactional(readOnly = true)
    public SectionSuggestionDTO improveSelection(UUID id, String text, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(promptBuilder.buildSectionImprovementSystemPrompt(
                        document.getLangue() != null ? document.getLangue().name() : null,
                        document.getTon() != null ? document.getTon().name() : null))
                .userPrompt(documentReferenceContextService.enrich(id, text))
                .build();
        SectionImprovement improvement = sectionImprovementResponseParser.parse(
                generationOrchestrator.generate(request).getContent());

        log.info("suggestion_outcome=GENERATED documentId={} scope=SELECTION confidence={}",
                id, improvement.getConfidence());

        SectionSuggestionDTO dto = new SectionSuggestionDTO();
        dto.setAiSuggestedContent(improvement.getContent());
        dto.setConfidenceScore(improvement.getConfidence());
        return dto;
    }

    /**
     * Composer IA de l'éditeur (panneau « Documents de référence » fixé sous
     * l'éditeur) : à partir d'une instruction libre, enrichie par le contexte
     * RAG des documents de référence attachés à ce document ({@link
     * DocumentReferenceContextService}), génère du contenu prêt à insérer à la
     * position du curseur côté client — jamais persisté ici, contrairement à
     * {@link #generateContent} : c'est {@code updateDocumentContent} (autosave)
     * qui sauvegardera le HTML final une fois le contenu inséré dans l'éditeur.
     */
    @Transactional(readOnly = true)
    public String generateAtCursor(UUID id, String instruction, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        String systemPrompt = promptBuilder.buildFreeformGenerationSystemPrompt(
                document.getLangue() != null ? document.getLangue().name() : null,
                document.getTon() != null ? document.getTon().name() : null);
        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(documentReferenceContextService.enrich(id, instruction))
                .build();
        String raw = generationOrchestrator.generate(request).getContent();
        return plainTextToHtml(raw);
    }

    @Transactional(readOnly = true)
    public DocumentDTO getById(UUID id, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);
        return toDto(document, documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> listForUser(UUID userId, UUID requestingUserId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que votre propre historique.");
        }
        return documentRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId).stream()
                .map(d -> toDto(d, documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(d.getId())))
                .toList();
    }

    /**
     * Assemble le contenu Markdown final à partir de {@code userContent} de
     * chaque section (sections vides ignorées), exporte en DOCX vers MinIO
     * (même mécanique que l'ancien export automatique de fin de génération
     * IA, réutilisée telle quelle) et marque le document {@code FINALISE}.
     */
    @Transactional
    public DocumentDTO finalizeDocument(UUID id, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        List<DocumentSection> sections = documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id);
        exportToObjectStorage(document, buildExportContent(document, sections));

        document.setStatut(DocumentStatut.FINALISE);
        document.setDateMaj(LocalDateTime.now());
        documentRepository.save(document);

        return toDto(document, sections);
    }

    /**
     * Export à la demande dans le format choisi (DOCX/PDF/Markdown), typiquement
     * juste après la finalisation — indépendant de l'archive DOCX déposée sur
     * MinIO par {@link #finalizeDocument} (qui, elle, sert de lien de
     * téléchargement persistant). Le contenu est réassemblé à la volée depuis
     * les sections, donc un export reflète toujours l'état courant du document.
     */
    @Transactional(readOnly = true)
    public ExportedFile export(UUID id, ExportFormat format, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        List<DocumentSection> sections = documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id);
        return exportService.export(buildExportContent(document, sections), format);
    }

    /**
     * En-tête/pied de page repris du Document Type (texte statique extrait du
     * fichier source, jamais généré par l'IA).
     * <p>
     * Le contenu est celui de l'éditeur ({@code contentHtml}) dès qu'il existe.
     * Le Markdown réassemblé depuis les sections n'est fourni que pour les
     * documents antérieurs à l'éditeur type Word — c'est ce qui les garde
     * exportables sans migration de données.
     */
    private ExportContent buildExportContent(Document document, List<DocumentSection> sections) {
        DocumentStructure structure = document.getDocumentType() != null
                ? documentStructureRepository.findByDocumentType_Id(document.getDocumentType().getId()).orElse(null)
                : null;
        boolean hasHtml = document.getContentHtml() != null && !document.getContentHtml().isBlank();
        return new ExportContent(resolvedTitle(document),
                hasHtml ? null : assembleContent(sections),
                document.getContentHtml(),
                structure != null ? structure.getHeaderText() : null,
                structure != null ? structure.getFooterText() : null);
    }

    /**
     * Le nom du Document Type fait office de titre pour un document généré
     * depuis un gabarit ; un document importé n'en a pas — son titre vient du
     * nom du fichier déposé, stocké dans {@code titre} à l'import (voir
     * {@link #importDocument}).
     */
    private String resolvedTitle(Document document) {
        if (document.getTitre() != null && !document.getTitre().isBlank()) {
            return document.getTitre();
        }
        return document.getDocumentType() != null ? document.getDocumentType().getNom() : "Document";
    }

    /** Même convention historique que {@code DocumentTypeExtractionService}/ancien flux : le nœud "cover" n'est pas une section éditable. */
    private void flatten(List<StructureNode> nodes, Document document, DocumentSection parent, List<DocumentSection> out) {
        if (nodes == null) {
            return;
        }
        for (StructureNode node : nodes) {
            if ("cover".equals(node.getType())) {
                continue;
            }
            DocumentSection section = DocumentSection.builder()
                    .document(document)
                    .parentSection(parent)
                    .sourceNodeId(node.getId())
                    .type(mapSectionType(node))
                    .level(node.getLevel())
                    .label(node.getLabel())
                    .tableColumns(resolveTableColumns(node))
                    .orderIndex(out.size())
                    .statut(DocumentSectionStatut.EMPTY)
                    .build();
            out.add(section);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flatten(node.getChildren(), document, section, out);
            }
        }
    }

    /**
     * Traduit le vocabulaire hérité de {@code StructureNode} ({@code
     * heading}+{@code level}, {@code table}, {@code paragraph_placeholder} —
     * conservé pour ne pas casser l'extraction déterministe/l'éditeur de
     * structure existants) vers le vocabulaire propre du nouveau modèle
     * relationnel ({@link DocumentSectionType}). {@code paragraph}/{@code list}
     * (Document Types importés) deviennent des emplacements à rédiger, comme
     * {@code paragraph_placeholder} (flux IA) — même traitement, un simple
     * slot de texte libre pour l'utilisateur.
     */
    private DocumentSectionType mapSectionType(StructureNode node) {
        String type = node.getType();
        if ("heading".equals(type)) {
            int level = node.getLevel() == null ? 1 : node.getLevel();
            if (level <= 1) return DocumentSectionType.TITLE;
            if (level == 2) return DocumentSectionType.SUBTITLE;
            return DocumentSectionType.SUB_SUBTITLE;
        }
        if ("table".equals(type)) {
            return DocumentSectionType.TABLE;
        }
        return DocumentSectionType.PARAGRAPH_PLACEHOLDER;
    }

    private List<TableColumnDef> resolveTableColumns(StructureNode node) {
        if (!"table".equals(node.getType())) {
            return null;
        }
        if (node.getTableColumns() != null) {
            return node.getTableColumns();
        }
        if (node.getColumns() != null) {
            return node.getColumns().stream().map(name -> TableColumnDef.builder().name(name).build()).toList();
        }
        return null;
    }

    private String assembleContent(List<DocumentSection> sections) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection section : sections) {
            String content = section.getUserContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(headingPrefix(section.getType())).append(section.getLabel()).append('\n').append(content);
        }
        return sb.toString();
    }

    private String headingPrefix(DocumentSectionType type) {
        return switch (type) {
            case TITLE -> "# ";
            case SUBTITLE -> "## ";
            case SUB_SUBTITLE -> "### ";
            default -> "";
        };
    }

    /** Dégradation volontaire (même choix que l'ancien export automatique) : un échec MinIO ne bloque pas la finalisation, seule l'URL de téléchargement reste absente. */
    private void exportToObjectStorage(Document document, ExportContent exportContent) {
        try {
            ExportedFile file = exportService.export(exportContent, ExportFormat.DOCX);
            String objectKey = "exports/" + document.getId() + "/" + file.filename();
            objectStorageService.upload(minioProperties.getBucketExports(), objectKey, file.content(), file.contentType());
            document.setMinioObjectKey(objectKey);
            document.setExportFormat(ExportFormat.DOCX.name());
        } catch (Exception e) {
            log.warn("Export automatique vers MinIO impossible pour le document {} : {}", document.getId(), e.getMessage());
        }
    }

    /**
     * Moyenne des sections effectivement évaluées — les sections sans score
     * (jamais passées par l'IA, ou réécrites depuis) sont exclues du calcul
     * plutôt que comptées à zéro : un document rédigé à la main afficherait
     * sinon 0 % de confiance, ce qui se lirait comme une alerte alors qu'il n'y
     * a simplement rien à évaluer.
     */
    private Integer averageConfidence(List<DocumentSection> sections) {
        OptionalDouble average = sections.stream()
                .map(DocumentSection::getConfidenceScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }

    private DocumentDTO toDto(Document document, List<DocumentSection> sections) {
        var dto = documentMapper.toDto(document);
        dto.setTitle(resolvedTitle(document));
        dto.setSections(documentSectionMapper.toDtoList(sections));
        dto.setGlobalConfidenceScore(averageConfidence(sections));
        if (document.getMinioObjectKey() != null) {
            try {
                dto.setExportUrl(objectStorageService.presignedGetUrl(minioProperties.getBucketExports(), document.getMinioObjectKey()));
            } catch (Exception e) {
                log.warn("URL pré-signée d'export indisponible pour le document {} : {}", document.getId(), e.getMessage());
            }
        }
        return dto;
    }

    Document findEntity(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document introuvable."));
    }

    void checkOwnership(Document document, UUID requestingUserId, boolean isAdmin) {
        UUID ownerId = document.getUtilisateur() != null ? document.getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Ce document ne vous appartient pas.");
        }
    }
}
