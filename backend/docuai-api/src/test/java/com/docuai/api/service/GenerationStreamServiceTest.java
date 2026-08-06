package com.docuai.api.service;

import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.rag.SimilaritySearchService;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SectionConstraintValidator;
import com.docuai.ai.service.StructuralValidator;
import com.docuai.api.config.GenerationProperties;
import com.docuai.core.model.Conversation;
import com.docuai.core.model.DocumentGenere;
import com.docuai.core.model.DocumentGenereStatut;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.GenerationSectionNode;
import com.docuai.core.model.SectionConstraints;
import com.docuai.core.repository.DocumentGenereRepository;
import com.docuai.core.repository.DocumentReferenceRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.export.ExportFormat;
import com.docuai.export.ExportService;
import com.docuai.export.ExportedFile;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageException;
import com.docuai.extraction.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verrou Redis anti-génération concurrente ({@code GenerationLockService}) :
 * refus propre si déjà verrouillé, libération garantie après succès et après
 * échec (finally), y compris avant même d'ouvrir la génération elle-même.
 */
@ExtendWith(MockitoExtension.class)
class GenerationStreamServiceTest {

    @Mock private DocumentGenereRepository documentGenereRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private DocumentReferenceRepository documentReferenceRepository;
    @Mock private GenerationService generationService;
    @Mock private PromptBuilder promptBuilder;
    @Mock private StructuralValidator structuralValidator;
    @Mock private GenerationOrchestrator generationOrchestrator;
    @Mock private SimilaritySearchService similaritySearchService;
    @Mock private GenerationProperties generationProperties;
    @Mock private GenerationLockService generationLockService;
    @Mock private SseEmitter emitter;
    @Mock private ExportService exportService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private MinioProperties minioProperties;
    private final SectionConstraintValidator sectionConstraintValidator = new SectionConstraintValidator();

    private GenerationStreamService service;

    private final UUID documentId = UUID.randomUUID();
    private final UUID requestingUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GenerationStreamService(documentGenereRepository, documentStructureRepository,
                documentReferenceRepository, generationService, promptBuilder, structuralValidator,
                generationOrchestrator, similaritySearchService, generationProperties, generationLockService,
                sectionConstraintValidator, exportService, objectStorageService, minioProperties);
    }

    /** À appeler dans les tests qui atteignent la branche de succès (!anyFailure) de runGeneration, où l'export automatique est déclenché. */
    private void stubSuccessfulExport() {
        when(minioProperties.getBucketExports()).thenReturn("docuai-exports");
        when(exportService.export(any(), eq(ExportFormat.DOCX)))
                .thenReturn(new ExportedFile(new byte[]{1, 2, 3}, "rapport.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    private DocumentGenere document() {
        DocumentType documentType = DocumentType.builder().id(UUID.randomUUID()).nom("Rapport").build();
        Conversation conversation = Conversation.builder().id(UUID.randomUUID()).build();
        return DocumentGenere.builder().id(documentId).documentType(documentType).conversation(conversation)
                .sections(new ArrayList<>()).build();
    }

    @Test
    void run_emitsConflictAndCompletes_whenLockNotAcquired() throws IOException {
        when(generationService.findEntity(documentId)).thenReturn(document());
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.empty());

        service.run(documentId, emitter, requestingUserId, false);

        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
        verifyNoInteractions(generationOrchestrator);
        verify(generationLockService, never()).release(any(), any());
    }

    @Test
    void run_releasesLock_afterSuccessfulGeneration() throws IOException {
        when(generationService.findEntity(documentId)).thenReturn(document());
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token-abc"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        verify(generationLockService).release(documentId, "token-abc");
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
    }

    @Test
    void run_releasesLock_evenWhenGenerationThrows() throws IOException {
        when(generationService.findEntity(documentId)).thenReturn(document());
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token-xyz"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenThrow(new RuntimeException("boom"));

        service.run(documentId, emitter, requestingUserId, false);

        verify(generationLockService).release(documentId, "token-xyz");
        verify(emitter).completeWithError(any());
    }

    @Test
    void run_resumesFromFirstNonDoneSection_withoutRegeneratingCompletedOnes() throws IOException {
        GenerationSectionNode intro = GenerationSectionNode.builder()
                .id("s1").label("Introduction").status("DONE").content("contenu intro").type("heading").level(2).build();
        GenerationSectionNode corps = GenerationSectionNode.builder()
                .id("s2").label("Corps").status("PENDING").content("").type("heading").level(2).build();

        DocumentGenere document = document();
        document.setSections(new ArrayList<>(List.of(intro, corps)));

        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token-resume"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(generationOrchestrator.streamGenerate(any()))
                .thenReturn(Flux.just(Chunk.builder().content("contenu corps").last(true).build()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        // Une seule génération IA déclenchée : la section déjà DONE n'est pas rejouée.
        verify(generationOrchestrator, times(1)).streamGenerate(any());
        // 2 saves seulement : la section "Corps" traitée + la mise à jour finale du statut (pas de save pour "Introduction", déjà persistée).
        verify(documentGenereRepository, times(2)).save(document);
        // 1 "section" rejoué (Introduction) + progress + 1 delta + section (Corps) + done = 5.
        verify(emitter, times(5)).send(any(SseEmitter.SseEventBuilder.class));

        assertThat(document.getContenu()).contains("contenu intro").contains("contenu corps");
        assertThat(intro.getContent()).isEqualTo("contenu intro"); // inchangée par la reprise
        assertThat(corps.getStatus()).isEqualTo("DONE");
    }

    @Test
    void run_retriesWithCorrectivePrompt_whenConstraintViolated_thenSucceeds() throws IOException {
        SectionConstraints constraints = SectionConstraints.builder().minLength(20).build();
        GenerationSectionNode section = GenerationSectionNode.builder()
                .id("s1").label("Résumé").status("PENDING").content("").type("paragraph").required(true)
                .constraints(constraints).build();

        DocumentGenere document = document();
        document.setSections(new ArrayList<>(List.of(section)));

        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(generationProperties.getSectionRetryAttempts()).thenReturn(1); // 2 tentatives au total

        when(generationOrchestrator.streamGenerate(any()))
                .thenReturn(Flux.just(Chunk.builder().content("trop court").last(true).build()))
                .thenReturn(Flux.just(Chunk.builder().content("cette fois le contenu est assez long pour la contrainte").last(true).build()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        ArgumentCaptor<GenerationRequest> captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationOrchestrator, times(2)).streamGenerate(captor.capture());
        assertThat(captor.getAllValues().get(0).getUserPrompt()).doesNotContain("corrige-la");
        assertThat(captor.getAllValues().get(1).getUserPrompt()).contains("corrige-la").contains("longueur minimale");

        assertThat(section.getStatus()).isEqualTo("DONE");
        assertThat(document.getStatut()).isEqualTo(DocumentGenereStatut.GENERE);
    }

    @Test
    void run_marksSectionFailed_whenConstraintViolationPersistsAcrossAllAttempts() throws IOException {
        SectionConstraints constraints = SectionConstraints.builder().minLength(500).build();
        GenerationSectionNode section = GenerationSectionNode.builder()
                .id("s1").label("Résumé").status("PENDING").content("").type("paragraph").required(true)
                .constraints(constraints).build();

        DocumentGenere document = document();
        document.setSections(new ArrayList<>(List.of(section)));

        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(generationProperties.getSectionRetryAttempts()).thenReturn(1); // 2 tentatives au total
        when(generationOrchestrator.streamGenerate(any()))
                .thenReturn(Flux.just(Chunk.builder().content("toujours trop court").last(true).build()));

        service.run(documentId, emitter, requestingUserId, false);

        verify(generationOrchestrator, times(2)).streamGenerate(any());
        assertThat(section.getStatus()).isEqualTo("FAILED");
        assertThat(section.getContent()).isEmpty();
        assertThat(document.getStatut()).isEqualTo(DocumentGenereStatut.ECHEC);
    }

    @Test
    void run_exportsToMinIO_andStoresObjectKey_afterFullSuccess() throws IOException {
        DocumentGenere document = document();
        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        verify(objectStorageService).upload(eq("docuai-exports"), eq("exports/" + documentId + "/rapport.docx"), any(), any());
        assertThat(document.getMinioObjectKey()).isEqualTo("exports/" + documentId + "/rapport.docx");
        assertThat(document.getExportFormat()).isEqualTo("DOCX");
        assertThat(document.getStatut()).isEqualTo(DocumentGenereStatut.GENERE);
    }

    @Test
    void run_degradesGracefully_whenMinioExportFails() throws IOException {
        DocumentGenere document = document();
        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(exportService.export(any(), eq(ExportFormat.DOCX))).thenThrow(new ObjectStorageException("MinIO indisponible.", new RuntimeException()));

        service.run(documentId, emitter, requestingUserId, false);

        // La génération elle-même a réussi : le statut ne doit pas être dégradé par un export raté.
        assertThat(document.getStatut()).isEqualTo(DocumentGenereStatut.GENERE);
        assertThat(document.getMinioObjectKey()).isNull();
        verify(emitter).complete();
        verify(emitter, never()).completeWithError(any());
        verify(generationLockService).release(eq(documentId), any());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> capturedEventPayloads() throws IOException {
        ArgumentCaptor<SseEmitter.SseEventBuilder> captor = ArgumentCaptor.forClass(SseEmitter.SseEventBuilder.class);
        verify(emitter, atLeastOnce()).send(captor.capture());
        // build() renvoie plusieurs fragments SSE (littéraux "data:"/"\n\n" inclus, cf. format
        // texte du protocole) — seul celui dont la donnée est notre Map (payload JSON) importe.
        return captor.getAllValues().stream()
                .flatMap(builder -> builder.build().stream())
                .map(ResponseBodyEmitter.DataWithMediaType::getData)
                .filter(Map.class::isInstance)
                .map(data -> (Map<String, Object>) data)
                .toList();
    }

    @Test
    void run_streamsDeltaEventsPerChunk_andAccumulatesFullSectionContent() throws IOException {
        GenerationSectionNode section = GenerationSectionNode.builder()
                .id("s1").label("Introduction").status("PENDING").content("").type("paragraph").build();
        DocumentGenere document = document();
        document.setSections(new ArrayList<>(List.of(section)));

        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(generationOrchestrator.streamGenerate(any())).thenReturn(Flux.just(
                Chunk.builder().content("Bon").last(false).build(),
                Chunk.builder().content("jour").last(false).build(),
                Chunk.builder().content(" le monde").last(true).build()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        List<Map<String, Object>> payloads = capturedEventPayloads();
        List<Map<String, Object>> deltas = payloads.stream().filter(p -> "delta".equals(p.get("type"))).toList();
        assertThat(deltas).hasSize(3);
        assertThat(deltas.stream().map(d -> (String) d.get("content"))).containsExactly("Bon", "jour", " le monde");
        assertThat(deltas).allMatch(d -> Integer.valueOf(0).equals(d.get("sectionIndex")));

        assertThat(section.getContent()).isEqualTo("Bonjour le monde");
        assertThat(document.getContenu()).contains("Bonjour le monde");

        List<Map<String, Object>> sectionEvents = payloads.stream().filter(p -> "section".equals(p.get("type"))).toList();
        assertThat(sectionEvents).hasSize(1);
        assertThat(sectionEvents.get(0).get("content")).isEqualTo("Bonjour le monde");
    }

    @Test
    void run_emitsFreshProgressEvent_onEachCorrectiveRetryAttempt() throws IOException {
        SectionConstraints constraints = SectionConstraints.builder().minLength(20).build();
        GenerationSectionNode section = GenerationSectionNode.builder()
                .id("s1").label("Résumé").status("PENDING").content("").type("paragraph").required(true)
                .constraints(constraints).build();
        DocumentGenere document = document();
        document.setSections(new ArrayList<>(List.of(section)));

        when(generationService.findEntity(documentId)).thenReturn(document);
        when(generationLockService.tryAcquire(eq(documentId), any())).thenReturn(Optional.of("token"));
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(promptBuilder.buildSystemPrompt(any(), any(), any(), any())).thenReturn("system prompt");
        when(promptBuilder.buildUserPrompt(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(documentReferenceRepository.findByConversation_IdOrderByDateImportAsc(any())).thenReturn(List.of());
        when(structuralValidator.validate(any(), any())).thenReturn(new StructuralValidator.ValidationResult(true, List.of()));
        when(generationProperties.getSectionRetryAttempts()).thenReturn(1); // 2 tentatives au total
        when(generationOrchestrator.streamGenerate(any()))
                .thenReturn(Flux.just(Chunk.builder().content("trop court").last(true).build()))
                .thenReturn(Flux.just(Chunk.builder().content("cette fois le contenu est assez long pour la contrainte").last(true).build()));
        stubSuccessfulExport();

        service.run(documentId, emitter, requestingUserId, false);

        List<Map<String, Object>> payloads = capturedEventPayloads();
        long progressCount = payloads.stream().filter(p -> "progress".equals(p.get("type"))).count();
        // Un événement "progress" par tentative (2), pas seulement à la première — signal de
        // réinitialisation de l'aperçu en direct côté client avant le retry correctif.
        assertThat(progressCount).isEqualTo(2);
    }
}
