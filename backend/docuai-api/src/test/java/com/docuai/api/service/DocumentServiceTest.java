package com.docuai.api.service;

import com.docuai.ai.dto.GeneratedSectionContent;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.api.dto.CreateDocumentRequest;
import com.docuai.api.dto.DocumentDTO;
import com.docuai.api.dto.GenerateDocumentRequest;
import com.docuai.api.event.DocumentGenerationCompletedEvent;
import com.docuai.api.mapper.DocumentMapper;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.DocumentStatut;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.DocumentRepository;
import com.docuai.core.repository.DocumentSectionRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.export.ExportContent;
import com.docuai.export.ExportFormat;
import com.docuai.export.ExportService;
import com.docuai.export.ExportedFile;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Aplatissement du squelette (parent/order, exclusion du nœud "cover"),
 * contrôle de propriété, et finalisation (assemblage + export) — voir
 * DocumentService#create/finalizeDocument.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private com.docuai.core.repository.DocumentReferenceRepository documentReferenceRepository;
    @Mock private DocumentSectionRepository documentSectionRepository;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentSectionMapper documentSectionMapper;
    @Mock private com.docuai.ai.service.GenerationOrchestrator generationOrchestrator;
    @Mock private com.docuai.ai.service.PromptBuilder promptBuilder;
    @Mock private com.docuai.ai.service.SectionImprovementResponseParser sectionImprovementResponseParser;
    @Mock private com.docuai.ai.service.DocumentContentResponseParser documentContentResponseParser;
    @Mock private FileIngestionService fileIngestionService;
    @Mock private com.docuai.extraction.html.DocxHtmlImporter docxHtmlImporter;
    @Mock private ExportService exportService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private MinioProperties minioProperties;
    @Mock private DocumentReferenceContextService documentReferenceContextService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DocumentService service;

    private final UUID documentTypeId = UUID.randomUUID();
    private final Utilisateur user = Utilisateur.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        // Constructeur réel plutôt que mock : c'est une fonction pure sur
        // l'arbre de structure, dont la sortie est justement ce qu'on veut
        // vérifier à la création.
        service = new DocumentService(documentRepository, documentReferenceRepository, documentSectionRepository, documentTypeRepository,
                documentStructureRepository, documentMapper, documentSectionMapper,
                new DocumentSkeletonHtmlBuilder(new DocumentContentHtmlBuilder()), new DocumentContentHtmlBuilder(),
                generationOrchestrator, promptBuilder, sectionImprovementResponseParser, documentContentResponseParser,
                fileIngestionService, docxHtmlImporter, exportService, objectStorageService, minioProperties,
                documentReferenceContextService, eventPublisher);
    }

    private DocumentType documentType() {
        return DocumentType.builder().id(documentTypeId).nom("Rapport d'audit").build();
    }

    /** cover (exclu) -> H1 "Introduction" -> [H2 "Contexte" (enfant), TABLE "Résultats" (enfant)]. */
    private List<StructureNode> sampleTree() {
        StructureNode subtitle = StructureNode.builder().id("n2").type("heading").level(2).label("Contexte").build();
        StructureNode table = StructureNode.builder().id("n3").type("table").label("Résultats").columns(List.of("Indicateur")).build();
        StructureNode title = StructureNode.builder().id("n1").type("heading").level(1).label("Introduction")
                .children(List.of(subtitle, table)).build();
        StructureNode cover = StructureNode.builder().id("cover").type("cover").label("Rapport d'audit").build();
        return List.of(cover, title);
    }

    @Test
    void create_flattensStructureIntoSections_excludingCover_withParentAndOrder() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(mock(DocumentDTO.class));

        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        service.create(request, user);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentSection>> captor = ArgumentCaptor.forClass(List.class);
        verify(documentSectionRepository).saveAll(captor.capture());
        List<DocumentSection> sections = captor.getValue();

        assertThat(sections).hasSize(3); // cover exclu : title, subtitle, table
        DocumentSection title = sections.get(0);
        DocumentSection subtitle = sections.get(1);
        DocumentSection table = sections.get(2);

        assertThat(title.getParentSection()).isNull();
        assertThat(title.getOrderIndex()).isZero();
        assertThat(subtitle.getParentSection()).isEqualTo(title);
        assertThat(subtitle.getOrderIndex()).isEqualTo(1);
        assertThat(table.getParentSection()).isEqualTo(title);
        assertThat(table.getOrderIndex()).isEqualTo(2);
        assertThat(table.getTableColumns()).extracting("name").containsExactly("Indicateur");
    }

    /** Au-delà de 20 documents pour l'utilisateur, le plus ancien doit être purgé — DB (delete) et stockage objet (export + documents de référence). */
    @Test
    void create_prunesOldestDocumentBeyondHistoryLimit_includingItsStorage() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(mock(DocumentDTO.class));

        // 21 documents déjà en historique (ordre décroissant de date, comme le tri réel) : le 21e (le plus ancien) dépasse la limite de 20 et doit être purgé.
        List<Document> existing = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            existing.add(Document.builder().id(UUID.randomUUID()).utilisateur(user).build());
        }
        Document oldest = Document.builder().id(UUID.randomUUID()).utilisateur(user).minioObjectKey("exports/old.docx").build();
        existing.add(oldest);
        when(documentRepository.findByUtilisateur_IdOrderByDateCreationDesc(user.getId())).thenReturn(existing);

        DocumentReference oldestReference = DocumentReference.builder()
                .id(UUID.randomUUID()).document(oldest).cheminStockage("references/old-ref.pdf").build();
        when(documentReferenceRepository.findByDocument_IdIn(List.of(oldest.getId()))).thenReturn(List.of(oldestReference));

        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        service.create(request, user);

        verify(documentRepository).delete(oldest);
        verify(objectStorageService).delete(any(), eq("exports/old.docx"));
        verify(objectStorageService).delete(any(), eq("references/old-ref.pdf"));
    }

    /** À 20 documents ou moins, aucune purge : la limite ne doit jamais supprimer un historique qui ne la dépasse pas. */
    @Test
    void create_doesNotPruneAnything_whenHistoryIsAtOrBelowLimit() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(mock(DocumentDTO.class));

        List<Document> existing = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            existing.add(Document.builder().id(UUID.randomUUID()).utilisateur(user).build());
        }
        when(documentRepository.findByUtilisateur_IdOrderByDateCreationDesc(user.getId())).thenReturn(existing);

        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        service.create(request, user);

        verify(documentRepository, never()).delete(any());
        verifyNoInteractions(objectStorageService);
    }

    /**
     * Le document s'ouvre directement dans l'éditeur type Word : sa structure
     * doit y être visible dès la création, sans quoi l'utilisateur arriverait
     * devant une page blanche.
     */
    @Test
    void create_rendersTheDocumentTypeSkeleton_asEditableHtml() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(mock(DocumentDTO.class));

        CreateDocumentRequest request = new CreateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        service.create(request, user);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHtml())
                .contains("<h1>Introduction</h1>")
                .contains("<h2>Contexte</h2>")
                .contains("<th><p>Indicateur</p></th>")
                // La page de garde devient une vraie page de garde, alors
                // qu'elle n'était pas une section éditable.
                .contains("data-page-break");
    }

    /**
     * Troisième point d'entrée de la rédaction : le plan du Document Type est
     * déjà fixé, l'IA ne fait que le remplir — le document créé porte le
     * contenu généré, le titre saisi par l'utilisateur (pas celui du Document
     * Type) et démarre directement en SAUVEGARDE (du contenu existe déjà, ce
     * n'est plus un squelette vierge).
     */
    @Test
    void generateContent_fillsTheTemplateWithAiContent_andStartsAsSauvegarde() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("peu importe").build());
        when(documentContentResponseParser.parse("peu importe")).thenReturn(List.of(
                GeneratedSectionContent.builder().id("n2").content("Contexte rédigé par l'IA.").build(),
                GeneratedSectionContent.builder().id("n3").rows(List.of(List.of("420k"))).build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        GenerateDocumentRequest request = new GenerateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        request.setName("Audit sécurité 2026");
        request.setDescription("Un audit de la sécurité informatique du siège.");
        DocumentDTO dto = service.generateContent(request, user);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getTitre()).isEqualTo("Audit sécurité 2026");
        assertThat(saved.getStatut()).isEqualTo(DocumentStatut.SAUVEGARDE);
        assertThat(saved.getContentHtml()).contains("Contexte rédigé par l'IA.").contains("<td><p>420k</p></td>");
        assertThat(dto.getTitle()).isEqualTo("Audit sécurité 2026");

        ArgumentCaptor<DocumentGenerationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentGenerationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().success()).isTrue();
        assertThat(eventCaptor.getValue().quotaExceeded()).isFalse();
        assertThat(eventCaptor.getValue().userId()).isEqualTo(user.getId());
    }

    /**
     * Le plan du Document Type reste la source de vérité : une réponse IA qui
     * inventerait un id absent du plan (ou une hallucination totale) ne doit
     * jamais faire apparaître de contenu hors du plan validé — seuls les ids
     * réellement présents dans l'arbre sont exploités.
     */
    @Test
    void generateContent_ignoresGeneratedContent_forIdsOutsideThePlan() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("peu importe").build());
        when(documentContentResponseParser.parse("peu importe")).thenReturn(List.of(
                GeneratedSectionContent.builder().id("id-invente-par-le-modele").content("Hors plan.").build()));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        GenerateDocumentRequest request = new GenerateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        request.setName("Audit");
        request.setDescription("Un audit.");
        service.generateContent(request, user);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHtml()).doesNotContain("Hors plan.");
    }

    /**
     * Le fournisseur IA échoue systématiquement (ou renvoie du JSON malformé) :
     * le document doit tout de même être créé — repli sur le squelette vide,
     * comme {@link #create}, plutôt qu'une erreur qui priverait l'utilisateur
     * du document.
     */
    @Test
    void generateContent_fallsBackToAnEmptySkeleton_whenAiGenerationKeepsFailing() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        when(generationOrchestrator.generate(any()))
                .thenThrow(new com.docuai.ai.exception.AiProviderException("Fournisseur indisponible."));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        GenerateDocumentRequest request = new GenerateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        request.setName("Audit");
        request.setDescription("Un audit.");
        DocumentDTO dto = service.generateContent(request, user);

        verify(generationOrchestrator, org.mockito.Mockito.times(3)).generate(any());
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHtml()).contains("<h2>Contexte</h2><p></p>");
        assertThat(dto).isNotNull();

        ArgumentCaptor<DocumentGenerationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentGenerationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().success()).isFalse();
        assertThat(eventCaptor.getValue().quotaExceeded()).isFalse();
    }

    /**
     * Un 429 (quota/rate limit IA dépassé) doit être signalé distinctement
     * d'une panne générique du fournisseur — {@link GenerationNotificationListener}
     * en tire un message différent (configurer un autre fournisseur plutôt que
     * "réessayer plus tard").
     */
    @Test
    void generateContent_flagsQuotaExceeded_whenAiProviderReturns429() {
        when(documentTypeRepository.findById(documentTypeId)).thenReturn(Optional.of(documentType()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId))
                .thenReturn(Optional.of(DocumentStructure.builder().arbreJson(sampleTree()).build()));
        org.springframework.web.reactive.function.client.WebClientResponseException tooManyRequests =
                org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        429, "Too Many Requests", org.springframework.http.HttpHeaders.EMPTY, new byte[0], null);
        when(generationOrchestrator.generate(any()))
                .thenThrow(new com.docuai.ai.exception.AiProviderException("Quota dépassé.", tooManyRequests));
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentSectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        GenerateDocumentRequest request = new GenerateDocumentRequest();
        request.setDocumentTypeId(documentTypeId);
        request.setName("Audit");
        request.setDescription("Un audit.");
        service.generateContent(request, user);

        ArgumentCaptor<DocumentGenerationCompletedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentGenerationCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().success()).isFalse();
        assertThat(eventCaptor.getValue().quotaExceeded()).isTrue();
    }

    /**
     * Second point d'entrée de la rédaction : un .docx déposé est converti
     * avec sa mise en forme (POI, via {@link com.docuai.extraction.html.DocxHtmlImporter}),
     * pas juste son texte — et le document créé n'a délibérément aucun
     * Document Type (rien à hériter d'un gabarit ici).
     */
    @Test
    void importDocument_convertsDocxWithFormatting_andHasNoDocumentType() {
        MockMultipartFile file = new MockMultipartFile("file", "Rapport annuel 2024.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "peu importe".getBytes());
        StoredFile stored = new StoredFile("Rapport_annuel_2024.docx", "sources/x/Rapport_annuel_2024.docx",
                file.getContentType(), "", "peu importe".getBytes());
        when(fileIngestionService.ingest(file, false)).thenReturn(stored);
        when(docxHtmlImporter.toHtml(stored.content())).thenReturn("<h1>Titre</h1><p>Corps <strong>en gras</strong>.</p>");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        DocumentDTO dto = service.importDocument(file, user);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        Document saved = captor.getValue();
        assertThat(saved.getDocumentType()).isNull();
        // Titre lisible dérivé du nom déposé — pas du nom assaini pour MinIO
        // (qui aurait remplacé l'espace par un underscore).
        assertThat(saved.getTitre()).isEqualTo("Rapport annuel 2024");
        assertThat(saved.getContentHtml()).isEqualTo("<h1>Titre</h1><p>Corps <strong>en gras</strong>.</p>");
        assertThat(saved.getStatut()).isEqualTo(DocumentStatut.BROUILLON);
        assertThat(dto.getTitle()).isEqualTo("Rapport annuel 2024");
    }

    /** PDF/texte/Markdown n'ont pas de convertisseur avec mise en forme : le texte brut extrait par Tika est reformé en paragraphes, sans passer par DocxHtmlImporter. */
    @Test
    void importDocument_paragraphizesPlainText_forNonDocxFormats() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "peu importe".getBytes());
        StoredFile stored = new StoredFile("notes.txt", "sources/x/notes.txt", "text/plain",
                "Premier paragraphe.\n\nSecond paragraphe.", "peu importe".getBytes());
        when(fileIngestionService.ingest(file, true)).thenReturn(stored);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            if (d.getId() == null) d.setId(UUID.randomUUID());
            return d;
        });
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        service.importDocument(file, user);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getContentHtml()).isEqualTo("<p>Premier paragraphe.</p><p>Second paragraphe.</p>");
        verifyNoInteractions(docxHtmlImporter);
    }

    /**
     * Premier enregistrement : le document quitte le squelette tout juste
     * créé (BROUILLON) pour SAUVEGARDE — c'est ce qui distingue dans
     * l'Historique un document réellement travaillé d'un squelette vierge.
     */
    @Test
    void saveContent_movesDocumentFromBrouillonToSauvegarde_onFirstSave() {
        Document document = Document.builder().id(UUID.randomUUID()).utilisateur(user)
                .statut(DocumentStatut.BROUILLON).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of());
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        service.saveContent(document.getId(), "<p>Contenu rédigé</p>", user.getId(), false);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(DocumentStatut.SAUVEGARDE);
    }

    /** Un enregistrement après coup ne doit pas défaire une finalisation ou un archivage déjà actés. */
    @Test
    void saveContent_leavesFinalizedOrArchivedStatus_unchanged() {
        Document document = Document.builder().id(UUID.randomUUID()).utilisateur(user)
                .statut(DocumentStatut.FINALISE).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of());
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        service.saveContent(document.getId(), "<p>Retouche</p>", user.getId(), false);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(DocumentStatut.FINALISE);
    }

    /** Un document importé n'a pas de Document Type pour fournir un titre à l'export : c'est celui dérivé du fichier déposé qui doit être utilisé. */
    @Test
    void export_usesTheDocumentTitre_whenThereIsNoDocumentType() {
        Document document = Document.builder().id(UUID.randomUUID()).utilisateur(user)
                .titre("Rapport importé").contentHtml("<p>Contenu</p>").build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of());
        when(exportService.export(any(ExportContent.class), any(ExportFormat.class)))
                .thenReturn(new ExportedFile(new byte[]{1}, "rapport.docx", "application/vnd.openxmlformats"));

        service.export(document.getId(), ExportFormat.DOCX, user.getId(), false);

        ArgumentCaptor<ExportContent> captor = ArgumentCaptor.forClass(ExportContent.class);
        verify(exportService).export(captor.capture(), any(ExportFormat.class));
        assertThat(captor.getValue().title()).isEqualTo("Rapport importé");
    }

    /** Dès qu'un document a été mis en forme dans l'éditeur, c'est ce contenu-là qui part à l'export — pas le Markdown des sections. */
    @Test
    void export_prefersTheEditorContent_overTheAssembledSections() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user)
                .contentHtml("<h1>Rédigé dans l'éditeur</h1>").build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of(
                DocumentSection.builder().label("Introduction")
                        .type(com.docuai.core.model.DocumentSectionType.TITLE).userContent("Ancien contenu.").build()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId)).thenReturn(Optional.empty());
        when(exportService.export(any(ExportContent.class), any(ExportFormat.class)))
                .thenReturn(new ExportedFile(new byte[]{1}, "rapport.docx", "application/vnd.openxmlformats"));

        service.export(document.getId(), ExportFormat.DOCX, user.getId(), false);

        ArgumentCaptor<ExportContent> captor = ArgumentCaptor.forClass(ExportContent.class);
        verify(exportService).export(captor.capture(), any(ExportFormat.class));
        assertThat(captor.getValue().contentHtml()).isEqualTo("<h1>Rédigé dans l'éditeur</h1>");
        assertThat(captor.getValue().content()).isNull();
    }

    @Test
    void export_assemblesCurrentSections_inRequestedFormat() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of(
                DocumentSection.builder().label("Introduction")
                        .type(com.docuai.core.model.DocumentSectionType.TITLE).userContent("Contenu rédigé.").build()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId)).thenReturn(Optional.empty());
        ExportedFile pdf = new ExportedFile(new byte[]{9}, "rapport.pdf", "application/pdf");
        when(exportService.export(any(ExportContent.class), any(ExportFormat.class))).thenReturn(pdf);

        ExportedFile result = service.export(document.getId(), ExportFormat.PDF, user.getId(), false);

        assertThat(result).isEqualTo(pdf);
        ArgumentCaptor<ExportContent> contentCaptor = ArgumentCaptor.forClass(ExportContent.class);
        ArgumentCaptor<ExportFormat> formatCaptor = ArgumentCaptor.forClass(ExportFormat.class);
        verify(exportService).export(contentCaptor.capture(), formatCaptor.capture());
        assertThat(formatCaptor.getValue()).isEqualTo(ExportFormat.PDF);
        assertThat(contentCaptor.getValue().content()).contains("Contenu rédigé.");
        assertThat(contentCaptor.getValue().title()).isEqualTo("Rapport d'audit");
    }

    /**
     * Le contenu d'une section TABLE est du Markdown (`| a | b |`) saisi via
     * l'éditeur de tableau du frontend : il doit traverser l'assemblage sans
     * préfixe de titre ni reformatage, sinon MarkdownContentParser
     * (docuai-export) ne le reconnaîtrait plus comme un vrai tableau.
     */
    @Test
    void export_keepsMarkdownTableIntact_forTableSections() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        String tableMarkdown = "| Indicateur | Valeur |\n| --- | --- |\n| CA | 420k€ |";
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of(
                DocumentSection.builder().label("Résultats")
                        .type(com.docuai.core.model.DocumentSectionType.TABLE).userContent(tableMarkdown).build()));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId)).thenReturn(Optional.empty());
        when(exportService.export(any(ExportContent.class), any(ExportFormat.class)))
                .thenReturn(new ExportedFile(new byte[]{1}, "rapport.docx", "application/vnd.openxmlformats"));

        service.export(document.getId(), ExportFormat.DOCX, user.getId(), false);

        ArgumentCaptor<ExportContent> captor = ArgumentCaptor.forClass(ExportContent.class);
        verify(exportService).export(captor.capture(), any(ExportFormat.class));
        assertThat(captor.getValue().content())
                .contains(tableMarkdown)
                .doesNotContain("# Résultats");
    }

    @Test
    void export_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.export(document.getId(), ExportFormat.DOCX, UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getById_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Document document = Document.builder().id(UUID.randomUUID()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.getById(document.getId(), UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Les sections non évaluées ne comptent pas comme des zéros : un document
     * dont une seule section est passée par l'IA ne doit pas afficher une
     * confiance globale effondrée par toutes les autres.
     */
    @Test
    void getById_averagesConfidenceOverScoredSectionsOnly() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId())).thenReturn(List.of(
                DocumentSection.builder().label("Évaluée haute").confidenceScore(85d).build(),
                DocumentSection.builder().label("Évaluée basse").confidenceScore(50d).build(),
                DocumentSection.builder().label("Jamais passée par l'IA").build()));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        DocumentDTO dto = service.getById(document.getId(), user.getId(), false);

        assertThat(dto.getGlobalConfidenceScore()).isEqualTo(68); // (85 + 50) / 2 arrondi
    }

    @Test
    void getById_leavesGlobalConfidenceNull_whenNoSectionHasBeenScored() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId()))
                .thenReturn(List.of(DocumentSection.builder().label("Rédigée à la main").build()));
        when(documentMapper.toDto(any(Document.class))).thenReturn(new DocumentDTO());

        assertThat(service.getById(document.getId(), user.getId(), false).getGlobalConfidenceScore()).isNull();
    }

    @Test
    void finalizeDocument_assemblesContentFromNonEmptySections_exportsAndMarksFinalise() {
        Document document = Document.builder().id(UUID.randomUUID()).documentType(documentType()).utilisateur(user)
                .statut(DocumentStatut.BROUILLON).build();
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        DocumentSection empty = DocumentSection.builder().label("Vide").userContent("").build();
        DocumentSection filled = DocumentSection.builder().label("Introduction")
                .type(com.docuai.core.model.DocumentSectionType.TITLE).userContent("Contenu rédigé.").build();
        when(documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(document.getId()))
                .thenReturn(List.of(empty, filled));
        when(documentStructureRepository.findByDocumentType_Id(documentTypeId)).thenReturn(Optional.empty());

        ExportedFile exportedFile = new ExportedFile(new byte[]{1, 2}, "rapport.docx", "application/vnd.openxmlformats");
        when(exportService.export(any(ExportContent.class), any(ExportFormat.class))).thenReturn(exportedFile);
        when(documentMapper.toDto(any(Document.class))).thenReturn(mock(DocumentDTO.class));

        service.finalizeDocument(document.getId(), user.getId(), false);

        ArgumentCaptor<ExportContent> contentCaptor = ArgumentCaptor.forClass(ExportContent.class);
        verify(exportService).export(contentCaptor.capture(), any(ExportFormat.class));
        assertThat(contentCaptor.getValue().content()).contains("Contenu rédigé.").doesNotContain("Vide");

        verify(objectStorageService).upload(any(), any(), any(), any());
        assertThat(document.getStatut()).isEqualTo(DocumentStatut.FINALISE);
        assertThat(document.getMinioObjectKey()).isNotNull();
    }
}
