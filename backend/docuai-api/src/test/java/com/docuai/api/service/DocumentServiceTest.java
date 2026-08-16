package com.docuai.api.service;

import com.docuai.api.dto.CreateDocumentRequest;
import com.docuai.api.dto.DocumentDTO;
import com.docuai.api.mapper.DocumentMapper;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
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
    @Mock private DocumentSectionRepository documentSectionRepository;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentSectionMapper documentSectionMapper;
    @Mock private com.docuai.ai.service.GenerationOrchestrator generationOrchestrator;
    @Mock private com.docuai.ai.service.PromptBuilder promptBuilder;
    @Mock private com.docuai.ai.service.SectionImprovementResponseParser sectionImprovementResponseParser;
    @Mock private FileIngestionService fileIngestionService;
    @Mock private com.docuai.extraction.html.DocxHtmlImporter docxHtmlImporter;
    @Mock private ExportService exportService;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private MinioProperties minioProperties;

    private DocumentService service;

    private final UUID documentTypeId = UUID.randomUUID();
    private final Utilisateur user = Utilisateur.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        // Constructeur réel plutôt que mock : c'est une fonction pure sur
        // l'arbre de structure, dont la sortie est justement ce qu'on veut
        // vérifier à la création.
        service = new DocumentService(documentRepository, documentSectionRepository, documentTypeRepository,
                documentStructureRepository, documentMapper, documentSectionMapper,
                new DocumentSkeletonHtmlBuilder(), generationOrchestrator, promptBuilder,
                sectionImprovementResponseParser, fileIngestionService, docxHtmlImporter,
                exportService, objectStorageService, minioProperties);
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
                file.getContentType(), "texte brut ignoré pour un docx", "peu importe".getBytes());
        when(fileIngestionService.ingest(file)).thenReturn(stored);
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
        when(fileIngestionService.ingest(file)).thenReturn(stored);
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
