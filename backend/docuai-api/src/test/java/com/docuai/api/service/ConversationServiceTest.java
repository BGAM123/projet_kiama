package com.docuai.api.service;

import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.rag.ChunkingService;
import com.docuai.ai.rag.EmbeddingService;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.api.config.ConversationProperties;
import com.docuai.api.config.GenerationProperties;
import com.docuai.api.dto.MessageDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.ConversationMapper;
import com.docuai.api.mapper.ReferenceDocumentMapper;
import com.docuai.core.model.Conversation;
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
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.extraction.text.TextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pagination de l'historique, fenêtre de contexte (résumé glissant amorti) et
 * cache Redis du bloc de structure — voir generateAssistantReply,
 * maybeExtendContextSummary et structureBlockFor dans ConversationService.
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private DocumentReferenceRepository documentReferenceRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private ConversationMapper conversationMapper;
    @Mock private ReferenceDocumentMapper referenceDocumentMapper;
    @Mock private TextExtractionService textExtractionService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private MinioProperties minioProperties;
    @Mock private GenerationProperties generationProperties;
    @Mock private GenerationOrchestrator generationOrchestrator;
    @Mock private PromptBuilder promptBuilder;
    @Mock private ChunkingService chunkingService;
    @Mock private EmbeddingService embeddingService;
    @Mock private DocumentTypeContextCacheService documentTypeContextCacheService;

    private final ConversationProperties conversationProperties = new ConversationProperties();

    private ConversationService service;

    private final UUID conversationId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        conversationProperties.setKeepLastMessages(2);
        conversationProperties.setSummaryBatchSize(2);
        service = new ConversationService(conversationRepository, messageRepository, documentReferenceRepository,
                documentChunkRepository, utilisateurRepository, documentTypeRepository, documentStructureRepository,
                conversationMapper, referenceDocumentMapper, textExtractionService, objectStorageService,
                minioProperties, generationProperties, conversationProperties, generationOrchestrator, promptBuilder,
                chunkingService, embeddingService, documentTypeContextCacheService);
    }

    private Conversation conversationOwnedBy(UUID userId) {
        return Conversation.builder()
                .id(conversationId)
                .utilisateur(Utilisateur.builder().id(userId).build())
                .build();
    }

    private Message message(MessageRole role, String content) {
        return Message.builder()
                .id(UUID.randomUUID())
                .role(role)
                .contenu(content)
                .dateCreation(LocalDateTime.now())
                .build();
    }

    private void stubMessageSaveIdentity() {
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void listMessages_throwsNotFound_whenConversationMissing() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMessages(conversationId, PageRequest.of(0, 20), ownerId, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listMessages_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversationOwnedBy(ownerId)));
        UUID otherUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.listMessages(conversationId, PageRequest.of(0, 20), otherUserId, false))
                .isInstanceOf(AccessDeniedException.class);

        verify(messageRepository, never())
                .findByConversation_IdAndRoleNotOrderByDateCreationAsc(any(), any(), any());
    }

    @Test
    void listMessages_returnsMappedPage_whenOwner() {
        Conversation conversation = conversationOwnedBy(ownerId);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));

        Message message = message(MessageRole.ASSISTANT, "Bonjour");
        message.setConversation(conversation);
        Pageable pageable = PageRequest.of(1, 5);
        Page<Message> page = new PageImpl<>(List.of(message), pageable, 11);
        when(messageRepository.findByConversation_IdAndRoleNotOrderByDateCreationAsc(conversationId, MessageRole.SYSTEM, pageable))
                .thenReturn(page);

        Page<MessageDTO> result = service.listMessages(conversationId, pageable, ownerId, false);

        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("Bonjour");
        assertThat(result.getContent().get(0).getRole()).isEqualTo("assistant");
    }

    @Test
    void listMessages_allowsAdmin_forOtherUsersConversation() {
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversationOwnedBy(ownerId)));
        Pageable pageable = PageRequest.of(0, 20);
        when(messageRepository.findByConversation_IdAndRoleNotOrderByDateCreationAsc(eq(conversationId), eq(MessageRole.SYSTEM), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<MessageDTO> result = service.listMessages(conversationId, pageable, UUID.randomUUID(), true);

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void sendMessage_belowWindowThreshold_sendsFullHistoryVerbatim_noSummaryCall() {
        Conversation conversation = conversationOwnedBy(ownerId);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        stubMessageSaveIdentity();

        // keepLastMessages = 2 : historique de 2 messages, sous le seuil.
        List<Message> history = List.of(message(MessageRole.USER, "Bonjour"), message(MessageRole.ASSISTANT, "Salut"));
        when(messageRepository.findByConversation_IdOrderByDateCreationAsc(conversationId)).thenReturn(history);
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("réponse").provider("MISTRAL").build());

        service.sendMessage(conversationId, "Nouveau message", ownerId, false);

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationOrchestrator, times(1)).generate(captor.capture());
        assertThat(captor.getValue().getHistory()).hasSize(2);
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void sendMessage_aboveWindowThreshold_extendsSummaryInBatch_andBoundsHistory() {
        Conversation conversation = conversationOwnedBy(ownerId);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        stubMessageSaveIdentity();

        // keepLastMessages=2, summaryBatchSize=2 : 5 messages -> fenêtre récente = 2 derniers,
        // 3 plus anciens à résumer (>= le lot de 2) -> résumé déclenché.
        List<Message> history = new ArrayList<>(List.of(
                message(MessageRole.USER, "m1"), message(MessageRole.ASSISTANT, "m2"), message(MessageRole.USER, "m3"),
                message(MessageRole.ASSISTANT, "m4"), message(MessageRole.USER, "m5")));
        when(messageRepository.findByConversation_IdOrderByDateCreationAsc(conversationId)).thenReturn(history);

        when(generationOrchestrator.generate(any())).thenAnswer(invocation -> {
            GenerationRequest request = invocation.getArgument(0);
            if (request.getSystemPrompt() != null && request.getSystemPrompt().contains("outil de résumé")) {
                return GenerationResult.builder().content("résumé condensé").provider("MISTRAL").build();
            }
            return GenerationResult.builder().content("réponse finale").provider("MISTRAL").build();
        });

        service.sendMessage(conversationId, "Nouveau message", ownerId, false);

        verify(generationOrchestrator, times(2)).generate(any());

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationOrchestrator, times(2)).generate(captor.capture());
        GenerationRequest replyRequest = captor.getAllValues().stream()
                .filter(r -> r.getSystemPrompt() != null && r.getSystemPrompt().contains("assistant DocuAI"))
                .findFirst().orElseThrow();
        assertThat(replyRequest.getHistory()).hasSize(2);
        assertThat(replyRequest.getSystemPrompt()).contains("résumé condensé");

        assertThat(conversation.getContextSummary()).isEqualTo("résumé condensé");
        assertThat(conversation.getContextSummaryUpToMessageId()).isEqualTo(history.get(2).getId());
        verify(conversationRepository, times(1)).save(conversation);
    }

    @Test
    void sendMessage_structureBlock_cacheHit_skipsPromptBuilderAndPut() {
        DocumentType documentType = DocumentType.builder().id(UUID.randomUUID()).nom("Rapport").build();
        Conversation conversation = conversationOwnedBy(ownerId);
        conversation.setDocumentType(documentType);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        stubMessageSaveIdentity();
        when(messageRepository.findByConversation_IdOrderByDateCreationAsc(conversationId)).thenReturn(List.of());
        when(documentTypeContextCacheService.get(documentType.getId())).thenReturn(Optional.of("structure en cache"));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("réponse").provider("MISTRAL").build());

        service.sendMessage(conversationId, "Nouveau message", ownerId, false);

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationOrchestrator).generate(captor.capture());
        assertThat(captor.getValue().getSystemPrompt()).contains("structure en cache");
        verify(promptBuilder, never()).renderStructureBlock(any());
        verify(documentTypeContextCacheService, never()).put(any(), any());
    }

    @Test
    void sendMessage_structureBlock_cacheMiss_computesAndCaches() {
        DocumentType documentType = DocumentType.builder().id(UUID.randomUUID()).nom("Rapport").build();
        Conversation conversation = conversationOwnedBy(ownerId);
        conversation.setDocumentType(documentType);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        stubMessageSaveIdentity();
        when(messageRepository.findByConversation_IdOrderByDateCreationAsc(conversationId)).thenReturn(List.of());
        when(documentTypeContextCacheService.get(documentType.getId())).thenReturn(Optional.empty());

        List<StructureNode> tree = List.of(StructureNode.builder().id("h1").type("heading").label("Introduction").build());
        DocumentStructure structure = DocumentStructure.builder().arbreJson(tree).build();
        when(documentStructureRepository.findByDocumentType_Id(documentType.getId())).thenReturn(Optional.of(structure));
        when(promptBuilder.renderStructureBlock(tree)).thenReturn("bloc calculé");
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("réponse").provider("MISTRAL").build());

        service.sendMessage(conversationId, "Nouveau message", ownerId, false);

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationOrchestrator).generate(captor.capture());
        assertThat(captor.getValue().getSystemPrompt()).contains("bloc calculé");
        verify(documentTypeContextCacheService).put(documentType.getId(), "bloc calculé");
    }
}
