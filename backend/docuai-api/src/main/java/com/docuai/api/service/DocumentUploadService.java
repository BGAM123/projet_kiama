package com.docuai.api.service;

import com.docuai.api.dto.ExtractedContentDTO;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint générique {@code POST /api/v1/documents/upload} : upload + extraction
 * de texte brut + URL de téléchargement pré-signée, sans créer de Document
 * Type (cf. DocumentTypeExtractionController.importDocumentType pour le flux
 * complet d'import qui, lui, crée le Document Type et sa structure).
 */
@Service
public class DocumentUploadService {

    private final FileIngestionService fileIngestionService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;

    public DocumentUploadService(FileIngestionService fileIngestionService,
                                  ObjectStorageService objectStorageService,
                                  MinioProperties minioProperties) {
        this.fileIngestionService = fileIngestionService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
    }

    public ExtractedContentDTO upload(MultipartFile file) {
        StoredFile stored = fileIngestionService.ingest(file);
        String fileUrl = objectStorageService.presignedGetUrl(minioProperties.getBucketSources(), stored.objectKey());

        ExtractedContentDTO dto = new ExtractedContentDTO();
        dto.setFileName(stored.fileName());
        dto.setMimeType(stored.mimeType());
        dto.setRawText(stored.rawText());
        dto.setFileUrl(fileUrl);
        return dto;
    }
}
