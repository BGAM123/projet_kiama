package com.docuai.extraction.service;

import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Service
public class DocumentParserService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParserService.class);
    private final Tika tika;

    public DocumentParserService() {
        this.tika = new Tika();
        tika.setMaxStringLength(-1); // Pour traiter les longs documents PDF/Word
    }

    public String extractText(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            log.info("Début de l'extraction de texte (Tika) pour {}", file.getOriginalFilename());
            return tika.parseToString(is);
        } catch (Exception e) {
            log.error("Erreur lors de l'extraction avec Apache Tika pour {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Echec de lecture du contenu du fichier", e);
        }
    }
    
    public String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return tika.detect(is, file.getOriginalFilename());
        } catch (Exception e) {
            return file.getContentType();
        }
    }
}
