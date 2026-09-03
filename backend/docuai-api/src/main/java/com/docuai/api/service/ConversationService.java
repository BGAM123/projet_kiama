package com.docuai.api.service;

import com.docuai.ai.dto.ChatMessage;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.rag.ChunkingService;
import com.docuai.ai.rag.EmbeddingService;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.api.config.ConversationProperties;
import com.docuai.api.config.GenerationProperties;
import com.docuai.api.dto.ConversationDTO;
import com.docuai.api.dto.CreateConversationRequest;
import com.docuai.api.dto.MessageDTO;
import com.docuai.api.dto.ReferenceDocumentDTO;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.ConversationMapper;
import com.docuai.api.mapper.ReferenceDocumentMapper;
import com.docuai.core.model.Conversation;
import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.Message;
import com.docuai.core.model.MessageRole;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.ConversationRepository;
import com.docuai.core.repository.DocumentChunkRepository;
import com.docuai.core.repository.DocumentReferenceRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.core.repository.MessageRepository;
import com.docuai.core.repository.UtilisateurRepository;
import com.docuai.api.util.FileNames;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.extraction.text.ExtractedText;
import com.docuai.extraction.text.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service applicatif : conversations, messages (chat IA réel via
 * {@link GenerationOrchestrator}) et documents de référence (upload MinIO +
 * indexation RAG). Un utilisateur ne peut agir que sur ses propres
 * conversations ({@code CONVERSATION_USE}) ; un ADMIN (toutes permissions)
 * peut consulter celles de n'importe qui — même pattern d'ownership que
 * {@link NotificationService#markRead}.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf", "md", "txt");

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final DocumentReferenceRepository documentReferenceRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final ConversationMapper conversationMapper;
    private final ReferenceDocumentMapper referenceDocumentMapper;
    private final TextExtractionService textExtractionService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;
    private final GenerationProperties generationProperties;
    private final ConversationProperties conversationProperties;
    private final GenerationOrchestrator generationOrchestrator;
    private final PromptBuilder promptBuilder;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final DocumentTypeContextCacheService documentTypeContextCacheService;

    public ConversationService(ConversationRepository conversationRepository,
                                MessageRepository messageRepository,
                                DocumentReferenceRepository documentReferenceRepository,
                                DocumentChunkRepository documentChunkRepository,
                                UtilisateurRepository utilisateurRepository,
                                DocumentTypeRepository documentTypeRepository,
                                DocumentStructureRepository documentStructureRepository,
                                ConversationMapper conversationMapper,
                                ReferenceDocumentMapper referenceDocumentMapper,
                                TextExtractionService textExtractionService,
                                ObjectStorageService objectStorageService,
                                MinioProperties minioProperties,
                                GenerationProperties generationProperties,
                                ConversationProperties conversationProperties,
                                GenerationOrchestrator generationOrchestrator,
                                PromptBuilder promptBuilder,
                                ChunkingService chunkingService,
                                EmbeddingService embeddingService,
                                DocumentTypeContextCacheService documentTypeContextCacheService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.documentReferenceRepository = documentReferenceRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.conversationMapper = conversationMapper;
        this.referenceDocumentMapper = referenceDocumentMapper;
        this.textExtractionService = textExtractionService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
        this.generationProperties = generationProperties;
        this.conversationProperties = conversationProperties;
        this.generationOrchestrator = generationOrchestrator;
        this.promptBuilder = promptBuilder;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.documentTypeContextCacheService = documentTypeContextCacheService;
    }

    @Transactional
    public ConversationDTO create(CreateConversationRequest request, UUID requestingUserId) {
        Utilisateur utilisateur = utilisateurRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Utilisateur introuvable."));
        if (!utilisateur.getId().equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez créer une conversation que pour vous-même.");
        }
        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));

        Conversation conversation = Conversation.builder()
                .utilisateur(utilisateur)
                .documentType(documentType)
                .titre(request.getTitle())
                .build();
        return conversationMapper.toDto(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ConversationDTO> listForUser(UUID userId) {
        return conversationMapper.toDtoList(conversationRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId));
    }

    @Transactional(readOnly = true)
    public Page<MessageDTO> listMessages(UUID conversationId, Pageable pageable, UUID requestingUserId, boolean isAdmin) {
        Conversation conversation = findConversation(conversationId);
        checkOwnership(conversation, requestingUserId, isAdmin);
        return messageRepository.findByConversation_IdAndRoleNotOrderByDateCreationAsc(conversationId, MessageRole.SYSTEM, pageable)
                .map(this::toMessageDto);
    }

    @Transactional
    public MessageDTO sendMessage(UUID conversationId, String content, UUID requestingUserId, boolean isAdmin) {
        Conversation conversation = findConversation(conversationId);
        checkOwnership(conversation, requestingUserId, isAdmin);

        Message userMessage = messageRepository.save(Message.builder()
                .conversation(conversation)
                .role(MessageRole.USER)
                .contenu(content)
                .build());

        String assistantReply = generateAssistantReply(conversation, conversationId, content);
        messageRepository.save(Message.builder()
                .conversation(conversation)
                .role(MessageRole.ASSISTANT)
                .contenu(assistantReply)
                .build());

        return toMessageDto(userMessage);
    }

    /**
     * Construit la réponse assistant avec une fenêtre de contexte bornée
     * (docuai.conversation.keep-last-messages) plutôt que l'historique
     * complet : au-delà de la fenêtre, les messages plus anciens sont
     * condensés dans {@code conversation.contextSummary} par
     * {@link #maybeExtendContextSummary} plutôt qu'envoyés verbatim — un
     * historique de dialogue est séquentiel (la continuité chronologique
     * récente prime), contrairement aux documents de référence où la
     * recherche par similarité pgvector ({@code SimilaritySearchService})
     * reste adaptée à un corpus hétérogène.
     */
    private String generateAssistantReply(Conversation conversation, UUID conversationId, String newContent) {
        try {
            List<Message> fullHistory = messageRepository.findByConversation_IdOrderByDateCreationAsc(conversationId).stream()
                    .filter(m -> m.getRole() != MessageRole.SYSTEM)
                    .toList();
            List<Message> recentWindow = recentWindow(fullHistory);
            maybeExtendContextSummary(conversation, fullHistory, recentWindow.size());

            List<ChatMessage> chatHistory = recentWindow.stream()
                    .map(m -> ChatMessage.builder().role(m.getRole().name()).content(m.getContenu()).build())
                    .toList();
            String documentTypeName = conversation.getDocumentType() != null ? conversation.getDocumentType().getNom() : "document";
            StringBuilder systemPrompt = new StringBuilder(
                    "Tu es l'assistant DocuAI. Tu aides l'utilisateur à préparer la génération d'un document de type « "
                            + documentTypeName + " ». Appuie-toi sur la structure attendue ci-dessous pour orienter tes "
                            + "propositions (titres, sections, tableaux) plutôt que d'inventer un plan générique, et intègre "
                            + "explicitement les recommandations exprimées par l'utilisateur dans la conversation. "
                            + "Réponds de façon concise et utile, en français sauf demande contraire.");
            if (conversation.getContextSummary() != null && !conversation.getContextSummary().isBlank()) {
                systemPrompt.append("\n\nRésumé des échanges antérieurs (plus anciens que les messages ci-dessous) :\n")
                        .append(conversation.getContextSummary());
            }
            String structureBlock = structureBlockFor(conversation);
            if (structureBlock != null && !structureBlock.isBlank()) {
                systemPrompt.append("\n\nStructure attendue du document « ").append(documentTypeName)
                        .append(" » (respecte l'ordre et les niveaux de titres, format Markdown) :\n")
                        .append(structureBlock);
            }

            GenerationRequest request = GenerationRequest.builder()
                    .systemPrompt(systemPrompt.toString())
                    .userPrompt(newContent)
                    .history(chatHistory)
                    .build();
            return generationOrchestrator.generate(request).getContent();
        } catch (AiProviderException e) {
            log.warn("Réponse IA indisponible pour la conversation {} : {}", conversationId, e.getMessage());
            return "Je ne peux pas répondre pour le moment (aucun fournisseur IA disponible). "
                    + "Réessayez plus tard ou contactez un administrateur.";
        }
    }

    /** Derniers messages envoyés verbatim au LLM ; au-delà, {@link #maybeExtendContextSummary} prend le relais. */
    private List<Message> recentWindow(List<Message> fullHistory) {
        int keep = conversationProperties.getKeepLastMessages();
        return fullHistory.size() > keep ? fullHistory.subList(fullHistory.size() - keep, fullHistory.size()) : fullHistory;
    }

    /**
     * Condense dans {@code conversation.contextSummary} les messages sortis
     * de la fenêtre récente, par lots d'au moins
     * {@code docuai.conversation.summary-batch-size} — sans ce seuil, un
     * appel LLM de résumé supplémentaire serait déclenché à chaque tour dès
     * que l'historique dépasse la fenêtre (l'ancien "reste" grandit de deux
     * messages — utilisateur + assistant — à chaque tour).
     */
    private void maybeExtendContextSummary(Conversation conversation, List<Message> fullHistory, int windowSize) {
        if (fullHistory.size() <= windowSize) {
            return;
        }
        List<Message> older = fullHistory.subList(0, fullHistory.size() - windowSize);
        int cursorIndex = -1;
        UUID upToId = conversation.getContextSummaryUpToMessageId();
        if (upToId != null) {
            for (int i = 0; i < older.size(); i++) {
                if (older.get(i).getId().equals(upToId)) {
                    cursorIndex = i;
                    break;
                }
            }
        }
        List<Message> toSummarize = older.subList(cursorIndex + 1, older.size());
        if (toSummarize.size() < conversationProperties.getSummaryBatchSize()) {
            return;
        }
        String extended = extendSummary(conversation.getId(), conversation.getContextSummary(), toSummarize);
        if (extended == null) {
            return; // échec IA dégradé : on retentera au prochain lot, le résumé existant reste utilisé tel quel.
        }
        conversation.setContextSummary(extended);
        conversation.setContextSummaryUpToMessageId(toSummarize.get(toSummarize.size() - 1).getId());
        conversationRepository.save(conversation);
    }

    private String extendSummary(UUID conversationId, String existingSummary, List<Message> newMessages) {
        StringBuilder transcript = new StringBuilder();
        for (Message m : newMessages) {
            transcript.append(m.getRole().name()).append(" : ").append(m.getContenu()).append('\n');
        }
        StringBuilder userPrompt = new StringBuilder(
                "Résume les échanges suivants en conservant les décisions, préférences et contraintes exprimées par "
                        + "l'utilisateur, de façon concise (quelques phrases, pas de mise en forme).");
        if (existingSummary != null && !existingSummary.isBlank()) {
            userPrompt.append("\n\nRésumé existant à mettre à jour :\n").append(existingSummary);
        }
        userPrompt.append("\n\nNouveaux échanges à intégrer :\n").append(transcript);

        GenerationRequest request = GenerationRequest.builder()
                .systemPrompt("Tu es un outil de résumé de conversation. Réponds uniquement avec le résumé mis à jour, sans préambule ni formule de politesse.")
                .userPrompt(userPrompt.toString())
                .build();
        try {
            return generationOrchestrator.generate(request).getContent();
        } catch (AiProviderException e) {
            log.warn("Extension du résumé de contexte impossible pour la conversation {} : {}", conversationId, e.getMessage());
            return null;
        }
    }

    /** Structure attendue du Document Type de la conversation (hors nœud "cover"), même filtrage que GenerationStreamService. */
    private List<StructureNode> expectedStructureFor(Conversation conversation) {
        if (conversation.getDocumentType() == null) {
            return List.of();
        }
        return documentStructureRepository.findByDocumentType_Id(conversation.getDocumentType().getId())
                .map(DocumentStructure::getArbreJson)
                .orElse(List.of())
                .stream()
                .filter(n -> !"cover".equals(n.getType()))
                .toList();
    }

    /** Bloc de structure attendue (Markdown) mis en cache Redis par Document Type — voir {@link DocumentTypeContextCacheService}. */
    private String structureBlockFor(Conversation conversation) {
        if (conversation.getDocumentType() == null) {
            return null;
        }
        UUID documentTypeId = conversation.getDocumentType().getId();
        return documentTypeContextCacheService.get(documentTypeId).orElseGet(() -> {
            List<StructureNode> expectedStructure = expectedStructureFor(conversation);
            String rendered = expectedStructure.isEmpty() ? "" : promptBuilder.renderStructureBlock(expectedStructure);
            documentTypeContextCacheService.put(documentTypeId, rendered);
            return rendered;
        });
    }

    @Transactional
    public ReferenceDocumentDTO addReferenceDocument(UUID conversationId, MultipartFile file, UUID requestingUserId, boolean isAdmin) {
        Conversation conversation = findConversation(conversationId);
        checkOwnership(conversation, requestingUserId, isAdmin);

        if (documentReferenceRepository.countByConversation_Id(conversationId) >= generationProperties.getMaxReferenceDocuments()) {
            throw BusinessException.conflict("TOO_MANY_REFERENCE_DOCUMENTS",
                    "Nombre maximal de documents de référence atteint pour cette conversation ("
                            + generationProperties.getMaxReferenceDocuments() + ").");
        }

        String originalName = FileNames.sanitize(file.getOriginalFilename());
        validateFile(file, originalName);
        byte[] content = readBytes(file);
        ExtractedText extracted = textExtractionService.extract(content);

        String objectKey = "references/" + conversationId + "/" + UUID.randomUUID() + "/" + originalName;
        objectStorageService.upload(minioProperties.getBucketReferences(), objectKey, content, extracted.mimeType());

        DocumentReference reference = documentReferenceRepository.save(DocumentReference.builder()
                .conversation(conversation)
                .nomFichier(originalName)
                .cheminStockage(objectKey)
                .build());

        indexForRag(reference, extracted.rawText());

        return referenceDocumentMapper.toDto(reference);
    }

    /**
     * Découpe + calcule les embeddings + persiste les chunks (RAG, Bloc 5)
     * pour un document de référence fraîchement importé. Dégradation
     * volontaire : un échec (ex. OPENAI_API_KEY absente) ne doit pas faire
     * échouer l'import lui-même — le document de référence reste utilisable
     * (affichable, supprimable), simplement absent de la recherche par
     * similarité tant qu'il n'est pas indexé — même principe que l'extraction
     * de structure du Bloc 4 qui dégrade en ECHEC_EXTRACTION sans annuler
     * l'import du fichier.
     */
    private void indexForRag(DocumentReference reference, String rawText) {
        try {
            List<DocumentChunk> chunks = chunkingService.chunk(reference.getId(), rawText);
            List<float[]> embeddings = embeddingService.embedAll(chunks.stream().map(DocumentChunk::getContenu).toList());
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }
            documentChunkRepository.saveAll(chunks);
        } catch (Exception e) {
            log.warn("Indexation RAG impossible pour le document de référence {} : {}", reference.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ReferenceDocumentDTO> listReferenceDocuments(UUID conversationId, UUID requestingUserId, boolean isAdmin) {
        Conversation conversation = findConversation(conversationId);
        checkOwnership(conversation, requestingUserId, isAdmin);
        return referenceDocumentMapper.toDtoList(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(conversationId));
    }

    private void validateFile(MultipartFile file, String fileName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier envoyé est vide.");
        }
        String extension = FileNames.extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Format non supporté (" + extension + "). Formats acceptés : "
                    + String.join(", ", ALLOWED_EXTENSIONS) + ".");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le fichier envoyé.", e);
        }
    }

    private MessageDTO toMessageDto(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversation().getId());
        dto.setRole(message.getRole().name().toLowerCase());
        dto.setContent(message.getContenu());
        dto.setCreatedAt(message.getDateCreation() != null ? message.getDateCreation().toString() : null);
        return dto;
    }

    Conversation findConversation(UUID id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CONVERSATION_NOT_FOUND", "Conversation introuvable."));
    }

    void checkOwnership(Conversation conversation, UUID requestingUserId, boolean isAdmin) {
        UUID ownerId = conversation.getUtilisateur() != null ? conversation.getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Cette conversation ne vous appartient pas.");
        }
    }
}
