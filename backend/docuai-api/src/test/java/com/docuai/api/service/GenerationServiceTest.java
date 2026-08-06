package com.docuai.api.service;

import com.docuai.api.dto.GeneratedDocumentDTO;
import com.docuai.api.dto.UpdateGenerationRequest;
import com.docuai.api.mapper.GenerationMapper;
import com.docuai.core.model.DocumentGenere;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.DocumentGenereRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.core.repository.UtilisateurRepository;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageException;
import com.docuai.extraction.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Résolution de l'URL de téléchargement pré-signée (export automatique MinIO,
 * étape 7) sur les DTO renvoyés par GenerationService, et invalidation de
 * l'export existant lors d'une édition manuelle du contenu.
 */
@ExtendWith(MockitoExtension.class)
class GenerationServiceTest {

    @Mock private DocumentGenereRepository documentGenereRepository;
    @Mock private DocumentTypeRepository documentTypeRepository;
    @Mock private DocumentStructureRepository documentStructureRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ConversationService conversationService;
    @Mock private GenerationMapper generationMapper;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private MinioProperties minioProperties;

    private GenerationService service;

    private final UUID documentId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GenerationService(documentGenereRepository, documentTypeRepository, documentStructureRepository,
                utilisateurRepository, conversationService, generationMapper, objectStorageService, minioProperties);
    }

    private DocumentGenere documentOwnedBy(UUID userId) {
        return DocumentGenere.builder().id(documentId).utilisateur(Utilisateur.builder().id(userId).build()).build();
    }

    @Test
    void getById_resolvesPresignedUrl_whenExportExists() {
        DocumentGenere document = documentOwnedBy(ownerId);
        document.setMinioObjectKey("exports/" + documentId + "/rapport.docx");
        when(documentGenereRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(generationMapper.toDto(document)).thenReturn(new GeneratedDocumentDTO());
        when(minioProperties.getBucketExports()).thenReturn("docuai-exports");
        when(objectStorageService.presignedGetUrl("docuai-exports", document.getMinioObjectKey()))
                .thenReturn("https://minio.local/presigned-url");

        GeneratedDocumentDTO dto = service.getById(documentId, ownerId, false);

        assertThat(dto.getExportUrl()).isEqualTo("https://minio.local/presigned-url");
    }

    @Test
    void getById_leavesExportUrlNull_whenNoExportYet() {
        DocumentGenere document = documentOwnedBy(ownerId);
        when(documentGenereRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(generationMapper.toDto(document)).thenReturn(new GeneratedDocumentDTO());

        GeneratedDocumentDTO dto = service.getById(documentId, ownerId, false);

        assertThat(dto.getExportUrl()).isNull();
        verify(objectStorageService, never()).presignedGetUrl(any(), any());
    }

    @Test
    void getById_degradesGracefully_whenPresignedUrlGenerationFails() {
        DocumentGenere document = documentOwnedBy(ownerId);
        document.setMinioObjectKey("exports/" + documentId + "/rapport.docx");
        when(documentGenereRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(generationMapper.toDto(document)).thenReturn(new GeneratedDocumentDTO());
        when(minioProperties.getBucketExports()).thenReturn("docuai-exports");
        when(objectStorageService.presignedGetUrl(any(), any())).thenThrow(new ObjectStorageException("MinIO indisponible.", new RuntimeException()));

        GeneratedDocumentDTO dto = service.getById(documentId, ownerId, false);

        assertThat(dto.getExportUrl()).isNull();
    }

    @Test
    void update_clearsStaleExport_whenContentChanges() {
        DocumentGenere document = documentOwnedBy(ownerId);
        document.setMinioObjectKey("exports/" + documentId + "/rapport.docx");
        document.setExportFormat("DOCX");
        when(documentGenereRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentGenereRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(generationMapper.toDto(any())).thenReturn(new GeneratedDocumentDTO());

        UpdateGenerationRequest request = new UpdateGenerationRequest();
        request.setContent("nouveau contenu édité manuellement");

        service.update(documentId, request, ownerId, false);

        assertThat(document.getMinioObjectKey()).isNull();
        assertThat(document.getExportFormat()).isNull();
        assertThat(document.getContenu()).isEqualTo("nouveau contenu édité manuellement");
    }
}
