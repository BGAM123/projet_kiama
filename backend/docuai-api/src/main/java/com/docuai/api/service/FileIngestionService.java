package com.docuai.api.service;

import com.docuai.api.config.UploadProperties;
import com.docuai.api.util.FileNames;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.extraction.text.ExtractedText;
import com.docuai.extraction.text.TextExtractionService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;
import java.util.UUID;

/**
 * Brique bas niveau partagée du Bloc 4 : valide un fichier source, en extrait
 * le texte brut (Tika) et le stocke dans MinIO (bucket
 * docuai.minio.bucket-sources). Utilisée à la fois par
 * {@link DocumentUploadService} (endpoint générique {@code /documents/upload})
 * et {@code DocumentTypeExtractionService} (import/ré-extraction d'un
 * Document Type) pour ne pas dupliquer validation/stockage/extraction texte.
 */
@Service
public class FileIngestionService {

    // Extensions acceptées, alignées sur le contrôle déjà fait côté frontend
    // (documents-types/import/page.tsx : /\.(docx?|pdf|md|txt)$/i) plutôt que
    // sur le seul Content-Type envoyé par le navigateur, peu fiable (ex. .md
    // envoyé en text/plain ou application/octet-stream selon l'OS/le navigateur).
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf", "md", "txt");

    private final TextExtractionService textExtractionService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;
    private final UploadProperties uploadProperties;

    public FileIngestionService(TextExtractionService textExtractionService,
                                 ObjectStorageService objectStorageService,
                                 MinioProperties minioProperties,
                                 UploadProperties uploadProperties) {
        this.textExtractionService = textExtractionService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
        this.uploadProperties = uploadProperties;
    }

    public StoredFile ingest(MultipartFile file) {
        String originalName = FileNames.sanitize(file.getOriginalFilename());
        validate(file, originalName);

        byte[] content = readBytes(file);
        ExtractedText extracted = textExtractionService.extract(content);

        String objectKey = "sources/" + UUID.randomUUID() + "/" + originalName;
        objectStorageService.upload(minioProperties.getBucketSources(), objectKey, content, extracted.mimeType());

        return new StoredFile(originalName, objectKey, extracted.mimeType(), extracted.rawText(), content);
    }

    private void validate(MultipartFile file, String fileName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier envoyé est vide.");
        }
        long maxBytes = (long) uploadProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale autorisée (" + uploadProperties.getMaxSizeMb() + " Mo).");
        }
        String extension = FileNames.extensionOf(fileName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Format non supporté (" + extension + "). Formats acceptés : "
                    + String.join(", ", ALLOWED_EXTENSIONS) + ".");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de lire le fichier envoyé.", e);
        }
    }
}
