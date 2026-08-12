package com.docuai.api.service;

import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.service.GenerationOrchestrator;
import com.docuai.ai.service.PromptBuilder;
import com.docuai.ai.service.SkeletonContentGuard;
import com.docuai.ai.service.SkeletonResponseParser;
import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.dto.GenerateDocumentTypeRequest;
import com.docuai.api.mapper.DocumentTypeMapper;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.CategorieRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Flux "décrire en texte -> squelette généré par IA" : succès direct, retry
 * après détection de contenu rédigé (SkeletonContentGuard), et épuisement des
 * tentatives -> ECHEC_EXTRACTION. PromptBuilder/SkeletonResponseParser/
 * SkeletonContentGuard sont instanciés réellement (aucune dépendance externe)
 * pour exercer la vraie logique de parsing/validation ; seule la frontière IA
 * (GenerationOrchestrator) et la persistance sont mockées.
 */
@ExtendWith(MockitoExtension.class)
class DocumentTypeGenerationServiceTest {

    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private CategorieRepository categorieRepository;
    @Mock private DocumentTypeMapper documentTypeMapper;
    @Mock private GenerationOrchestrator generationOrchestrator;
    @Mock private DocumentTypeContextCacheService documentTypeContextCacheService;

    private DocumentTypeGenerationService service;

    private final UUID categoryId = UUID.randomUUID();
    private final Categorie categorie = Categorie.builder().id(categoryId).build();

    private static final String VALID_SKELETON = """
            [
              {"id": "t1", "type": "heading", "level": 1, "label": "Introduction"},
              {"id": "t2", "type": "heading", "level": 2, "label": "Contexte"},
              {"id": "t3", "type": "table", "label": "Résultats", "tableColumns": [{"name": "Indicateur", "type": "text"}]}
            ]
            """;

    private static final String WRITTEN_CONTENT_SKELETON = """
            [
              {"id": "t1", "type": "heading", "level": 1, "label": "Ce rapport présente en détail l'ensemble des constats réalisés lors de l'audit interne mené sur la période concernée."}
            ]
            """;

    @BeforeEach
    void setUp() {
        service = new DocumentTypeGenerationService(
                documentTypeRepository, documentStructureRepository, categorieRepository,
                documentTypeMapper, generationOrchestrator, new PromptBuilder(),
                new SkeletonResponseParser(), new SkeletonContentGuard(), documentTypeContextCacheService);

        when(categorieRepository.findById(categoryId)).thenReturn(Optional.of(categorie));
        when(documentTypeRepository.save(any(DocumentType.class))).thenAnswer(inv -> {
            DocumentType dt = inv.getArgument(0);
            if (dt.getId() == null) dt.setId(UUID.randomUUID());
            return dt;
        });
        when(documentTypeMapper.toDto(any(DocumentType.class))).thenReturn(mock(DocumentTypeDTO.class));
    }

    private GenerateDocumentTypeRequest request() {
        GenerateDocumentTypeRequest req = new GenerateDocumentTypeRequest();
        req.setDescription("Rapport d'audit interne");
        req.setName("Rapport d'audit");
        req.setCategoryId(categoryId);
        return req;
    }

    @Test
    void generate_succeedsOnFirstAttempt_persistsAiGeneratedStructure() {
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(documentStructureRepository.save(any(DocumentStructure.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generationOrchestrator.generate(any())).thenReturn(GenerationResult.builder().content(VALID_SKELETON).build());

        service.generate(request(), Utilisateur.builder().build());

        verify(generationOrchestrator, times(1)).generate(any());

        ArgumentCaptor<DocumentStructure> structureCaptor = ArgumentCaptor.forClass(DocumentStructure.class);
        verify(documentStructureRepository).save(structureCaptor.capture());
        assertThat(structureCaptor.getValue().getSource()).isEqualTo("AI_GENERATED");
        assertThat(structureCaptor.getValue().getArbreJson()).hasSize(4); // cover + 3 nœuds générés

        ArgumentCaptor<DocumentType> typeCaptor = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentTypeRepository, times(2)).save(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getStatut()).isEqualTo(DocumentTypeStatut.STRUCTURE_EXTRAITE);

        verify(documentTypeContextCacheService).evict(any());
    }

    @Test
    void generate_retriesAfterWrittenContentDetected_thenSucceeds() {
        when(documentStructureRepository.findByDocumentType_Id(any())).thenReturn(Optional.empty());
        when(documentStructureRepository.save(any(DocumentStructure.class))).thenAnswer(inv -> inv.getArgument(0));
        when(generationOrchestrator.generate(any()))
                .thenReturn(GenerationResult.builder().content(WRITTEN_CONTENT_SKELETON).build())
                .thenReturn(GenerationResult.builder().content(VALID_SKELETON).build());

        service.generate(request(), Utilisateur.builder().build());

        verify(generationOrchestrator, times(2)).generate(any());

        ArgumentCaptor<DocumentType> typeCaptor = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentTypeRepository, times(2)).save(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getStatut()).isEqualTo(DocumentTypeStatut.STRUCTURE_EXTRAITE);
    }

    @Test
    void generate_exhaustsRetries_marksEchecExtraction() {
        when(generationOrchestrator.generate(any()))
                .thenReturn(GenerationResult.builder().content(WRITTEN_CONTENT_SKELETON).build());

        service.generate(request(), Utilisateur.builder().build());

        verify(generationOrchestrator, times(3)).generate(any());
        verify(documentStructureRepository, times(0)).save(any());

        ArgumentCaptor<DocumentType> typeCaptor = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentTypeRepository, times(2)).save(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getStatut()).isEqualTo(DocumentTypeStatut.ECHEC_EXTRACTION);
    }

    @Test
    void generate_degradesToEchecExtraction_whenAiProviderUnavailable() {
        when(generationOrchestrator.generate(any())).thenThrow(new AiProviderException("indisponible"));

        service.generate(request(), Utilisateur.builder().build());

        verify(generationOrchestrator, times(3)).generate(any());

        ArgumentCaptor<DocumentType> typeCaptor = ArgumentCaptor.forClass(DocumentType.class);
        verify(documentTypeRepository, times(2)).save(typeCaptor.capture());
        assertThat(typeCaptor.getValue().getStatut()).isEqualTo(DocumentTypeStatut.ECHEC_EXTRACTION);
    }
}
