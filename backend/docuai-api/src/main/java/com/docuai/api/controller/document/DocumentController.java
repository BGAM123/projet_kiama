package com.docuai.api.controller.document;

import com.docuai.extraction.dto.ExtractedContentDetail;
import com.docuai.extraction.service.ExtractionFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final ExtractionFacade extractionFacade;

    public DocumentController(ExtractionFacade extractionFacade) {
        this.extractionFacade = extractionFacade;
    }

    @PostMapping("/upload")
    public ResponseEntity<ExtractedContentDetail> uploadAndExtract(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(extractionFacade.processAndStore(file));
    }
}
