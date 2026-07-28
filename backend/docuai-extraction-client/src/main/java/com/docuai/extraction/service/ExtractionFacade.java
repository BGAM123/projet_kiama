package com.docuai.extraction.service;

import com.docuai.extraction.dto.ExtractedContentDetail;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExtractionFacade {
    
    private final StorageService storageService;
    private final DocumentParserService parserService;

    public ExtractionFacade(StorageService storageService, DocumentParserService parserService) {
        this.storageService = storageService;
        this.parserService = parserService;
    }
    
    public ExtractedContentDetail processAndStore(MultipartFile file) {
        // 1. Détection du type de fichier
        String mimeType = parserService.detectMimeType(file);
        
        // 2. Extraction brute du texte via Tika
        String text = parserService.extractText(file);
        
        // 3. Upload sur MinIO object storage
        String objectName = storageService.uploadFile(file);
        String fileUrl = storageService.getFileUrl(objectName);
        
        return ExtractedContentDetail.builder()
                .fileName(file.getOriginalFilename())
                .mimeType(mimeType)
                .rawText(text)
                .fileUrl(fileUrl)
                .build();
    }
}
