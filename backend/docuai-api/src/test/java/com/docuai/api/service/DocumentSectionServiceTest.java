package com.docuai.api.service;

import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SectionImprovementResponseParser;
import com.docuai.api.dto.DocumentSectionDTO;
import com.docuai.api.dto.SectionSuggestionDTO;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.DocumentSectionHistory;
import com.docuai.core.model.DocumentSectionHistorySource;
import com.docuai.core.model.DocumentSectionStatut;
import com.docuai.core.model.Language;
import com.docuai.core.model.Tone;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.DocumentSectionHistoryRepository;
import com.docuai.core.repository.DocumentSectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sauvegarde de contenu (+ historique), amélioration IA (suggestion distincte
 * du contenu retenu), application/rejet explicite de la suggestion — voir
 * DocumentSectionService.
 */
@ExtendWith(MockitoExtension.class)
class DocumentSectionServiceTest {

    @Mock private DocumentSectionRepository documentSectionRepository;
    @Mock private DocumentSectionHistoryRepository documentSectionHistoryRepository;
    @Mock private DocumentSectionMapper documentSectionMapper;
    @Mock private GenerationOrchestrator generationOrchestrator;
    @Mock private DocumentReferenceContextService documentReferenceContextService;

    private DocumentSectionService service;

    private final Utilisateur user = Utilisateur.builder().id(UUID.randomUUID()).build();
    private final UUID documentId = UUID.randomUUID();
    private final UUID sectionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DocumentSectionService(documentSectionRepository, documentSectionHistoryRepository,
                documentSectionMapper, generationOrchestrator, new PromptBuilder(),
                new SectionImprovementResponseParser(), documentReferenceContextService);
    }

    private DocumentSection sectionOwnedBy(Utilisateur owner) {
        Document document = Document.builder().id(documentId).utilisateur(owner)
                .langue(Language.FR).ton(Tone.NEUTRE).build();
        return DocumentSection.builder().id(sectionId).document(document).statut(DocumentSectionStatut.EMPTY).build();
    }

    @Test
    void saveContent_withNonBlankContent_setsDraftedStatus_andRecordsHistory() {
        DocumentSection section = sectionOwnedBy(user);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentSectionMapper.toDto(any(DocumentSection.class))).thenReturn(mock(DocumentSectionDTO.class));

        service.saveContent(documentId, sectionId, "Contenu rédigé par l'utilisateur.", user.getId(), false);

        assertThat(section.getUserContent()).isEqualTo("Contenu rédigé par l'utilisateur.");
        assertThat(section.getStatut()).isEqualTo(DocumentSectionStatut.DRAFTED);

        ArgumentCaptor<DocumentSectionHistory> captor = ArgumentCaptor.forClass(DocumentSectionHistory.class);
        verify(documentSectionHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(DocumentSectionHistorySource.USER);
    }

    @Test
    void saveContent_withBlankContent_setsEmptyStatus_noHistory() {
        DocumentSection section = sectionOwnedBy(user);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentSectionMapper.toDto(any(DocumentSection.class))).thenReturn(mock(DocumentSectionDTO.class));

        service.saveContent(documentId, sectionId, "   ", user.getId(), false);

        assertThat(section.getStatut()).isEqualTo(DocumentSectionStatut.EMPTY);
        verify(documentSectionHistoryRepository, never()).save(any());
    }

    @Test
    void improve_throwsBusinessException_whenSectionEmpty() {
        DocumentSection section = sectionOwnedBy(user);
        section.setUserContent(null);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.improve(documentId, sectionId, user.getId(), false))
                .isInstanceOf(BusinessException.class);
    }

    /** Réponse hors format (texte brut) : la suggestion reste exploitable, seul le score manque. */
    @Test
    void improve_setsAiSuggestedContent_withoutChangingUserContent() {
        DocumentSection section = sectionOwnedBy(user);
        section.setUserContent("Texte original.");
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentReferenceContextService.enrich(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content("Texte amélioré.").build());

        SectionSuggestionDTO result = service.improve(documentId, sectionId, user.getId(), false);

        assertThat(result.getAiSuggestedContent()).isEqualTo("Texte amélioré.");
        assertThat(result.getConfidenceScore()).isNull();
        assertThat(section.getUserContent()).isEqualTo("Texte original.");
        assertThat(section.getAiSuggestedContent()).isEqualTo("Texte amélioré.");
        assertThat(section.getConfidenceScore()).isNull();
        assertThat(section.getStatut()).isEqualTo(DocumentSectionStatut.EMPTY); // statut inchangé tant que non appliqué
    }

    @Test
    void improve_storesConfidenceScore_whenProviderRespectsJsonFormat() {
        DocumentSection section = sectionOwnedBy(user);
        section.setUserContent("Texte original.");
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentReferenceContextService.enrich(any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder()
                .content("{\"content\": \"Texte amélioré.\", \"confidence\": 82}").build());

        SectionSuggestionDTO result = service.improve(documentId, sectionId, user.getId(), false);

        assertThat(result.getAiSuggestedContent()).isEqualTo("Texte amélioré.");
        assertThat(result.getConfidenceScore()).isEqualTo(82d);
        assertThat(section.getConfidenceScore()).isEqualTo(82d);
    }

    @Test
    void saveContent_clearsConfidenceScore_becauseItDescribedTheAiOutput() {
        DocumentSection section = sectionOwnedBy(user);
        section.setConfidenceScore(90d);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentSectionMapper.toDto(any(DocumentSection.class))).thenReturn(mock(DocumentSectionDTO.class));

        service.saveContent(documentId, sectionId, "Réécrit à la main.", user.getId(), false);

        assertThat(section.getConfidenceScore()).isNull();
    }

    @Test
    void applySuggestion_throwsBusinessException_whenNoSuggestionPending() {
        DocumentSection section = sectionOwnedBy(user);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.applySuggestion(documentId, sectionId, user.getId(), false))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applySuggestion_copiesToUserContent_setsAiImprovedStatus_recordsHistory_clearsSuggestion() {
        DocumentSection section = sectionOwnedBy(user);
        section.setUserContent("Texte original.");
        section.setAiSuggestedContent("Texte amélioré.");
        section.setConfidenceScore(82d);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentSectionMapper.toDto(any(DocumentSection.class))).thenReturn(mock(DocumentSectionDTO.class));

        service.applySuggestion(documentId, sectionId, user.getId(), false);

        assertThat(section.getUserContent()).isEqualTo("Texte amélioré.");
        assertThat(section.getAiSuggestedContent()).isNull();
        assertThat(section.getConfidenceScore()).isEqualTo(82d); // le score suit le texte retenu
        assertThat(section.getStatut()).isEqualTo(DocumentSectionStatut.AI_IMPROVED);

        ArgumentCaptor<DocumentSectionHistory> captor = ArgumentCaptor.forClass(DocumentSectionHistory.class);
        verify(documentSectionHistoryRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(DocumentSectionHistorySource.AI_APPLIED);
        assertThat(captor.getValue().getContent()).isEqualTo("Texte amélioré.");
    }

    @Test
    void rejectSuggestion_clearsSuggestion_withoutTouchingUserContent() {
        DocumentSection section = sectionOwnedBy(user);
        section.setUserContent("Texte original.");
        section.setAiSuggestedContent("Suggestion rejetée.");
        section.setConfidenceScore(45d);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));
        when(documentSectionMapper.toDto(any(DocumentSection.class))).thenReturn(mock(DocumentSectionDTO.class));

        service.rejectSuggestion(documentId, sectionId, user.getId(), false);

        assertThat(section.getAiSuggestedContent()).isNull();
        assertThat(section.getConfidenceScore()).isNull();
        assertThat(section.getUserContent()).isEqualTo("Texte original.");
        verify(documentSectionHistoryRepository, never()).save(any());
    }

    @Test
    void saveContent_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        DocumentSection section = sectionOwnedBy(user);
        when(documentSectionRepository.findById(sectionId)).thenReturn(Optional.of(section));

        assertThatThrownBy(() -> service.saveContent(documentId, sectionId, "x", UUID.randomUUID(), false))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
