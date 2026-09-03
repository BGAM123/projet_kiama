package com.docuai.api.service;

import com.docuai.ai.rag.ChunkingService;
import com.docuai.ai.rag.EmbeddingService;
import com.docuai.api.config.GenerationProperties;
import com.docuai.api.dto.ReferenceDocumentDTO;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.ReferenceDocumentMapper;
import com.docuai.api.util.FileNames;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.DocumentReference;
import com.docuai.core.repository.DocumentChunkRepository;
import com.docuai.core.repository.DocumentReferenceRepository;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.extraction.text.ExtractedText;
import com.docuai.extraction.text.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Documents de référence attachés directement à un {@link Document} en cours
 * d'édition — pendant de {@code ConversationService#addReferenceDocument}
 * pour les conversations (Bloc 6), anticipé par le commentaire de
 * V9__document_manual_editing.sql ("potentiellement réutilisables plus tard
 * comme contexte pour le bouton Améliorer avec l'IA"). Mêmes règles
 * (extensions, taille, dégradation silencieuse de l'indexation RAG) : voir
 * {@link ConversationService#indexForRag(DocumentReference, String)} pour le
 * raisonnement détaillé, non dupliqué ici.
 * <p>
 * Réutilise {@link DocumentService#findEntity} / {@link DocumentService#checkOwnership}
 * (mêmes règles de propriété qu'un accès normal au document) plutôt que de les
 * dupliquer.
 */
@Service
public class DocumentReferenceService {

    private static final Logger log = LoggerFactory.getLogger(DocumentReferenceService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("doc", "docx", "pdf", "md", "txt");

    private final DocumentService documentService;
    private final DocumentReferenceRepository documentReferenceRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ReferenceDocumentMapper referenceDocumentMapper;
    private final GenerationProperties generationProperties;
    private final TextExtractionService textExtractionService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;

    public DocumentReferenceService(DocumentService documentService,
                                     DocumentReferenceRepository documentReferenceRepository,
                                     DocumentChunkRepository documentChunkRepository,
                                     ReferenceDocumentMapper referenceDocumentMapper,
                                     GenerationProperties generationProperties,
                                     TextExtractionService textExtractionService,
                                     ChunkingService chunkingService,
                                     EmbeddingService embeddingService,
                                     ObjectStorageService objectStorageService,
                                     MinioProperties minioProperties) {
        this.documentService = documentService;
        this.documentReferenceRepository = documentReferenceRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.referenceDocumentMapper = referenceDocumentMapper;
        this.generationProperties = generationProperties;
        this.textExtractionService = textExtractionService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
    }

    @Transactional
    public ReferenceDocumentDTO addReferenceDocument(UUID documentId, MultipartFile file, UUID requestingUserId, boolean isAdmin) {
        Document document = documentService.findEntity(documentId);
        documentService.checkOwnership(document, requestingUserId, isAdmin);

        if (documentReferenceRepository.countByDocument_Id(documentId) >= generationProperties.getMaxReferenceDocuments()) {
            throw BusinessException.conflict("TOO_MANY_REFERENCE_DOCUMENTS",
                    "Nombre maximal de documents de référence atteint pour ce document ("
                            + generationProperties.getMaxReferenceDocuments() + ").");
        }

        String originalName = FileNames.sanitize(file.getOriginalFilename());
        validateFile(file, originalName);
        byte[] content = readBytes(file);
        ExtractedText extracted = textExtractionService.extract(content);

        String objectKey = "references/documents/" + documentId + "/" + UUID.randomUUID() + "/" + originalName;
        objectStorageService.upload(minioProperties.getBucketReferences(), objectKey, content, extracted.mimeType());

        DocumentReference reference = documentReferenceRepository.save(DocumentReference.builder()
                .document(document)
                .nomFichier(originalName)
                .cheminStockage(objectKey)
                .build());

        indexForRag(reference, extracted.rawText());

        return referenceDocumentMapper.toDto(reference);
    }

    /** Dégradation volontaire identique à {@code ConversationService#indexForRag} : un échec n'annule pas l'import. */
    private void indexForRag(DocumentReference reference, String rawText) {
        try {
            List<DocumentChunk> chunks = chunkingService.chunk(reference.getId(), rawText);
            List<float[]> embeddings = embeddingService.embedAll(chunks.stream().map(DocumentChunk::getContenu).toList());
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).setEmbedding(embeddings.get(i));
            }
            documentChunkRepository.saveAll(chunks);
        } catch (Exception e) {
            log.warn("Indexation RAG impossible pour le document de référence {} : {}", reference.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ReferenceDocumentDTO> listReferenceDocuments(UUID documentId, UUID requestingUserId, boolean isAdmin) {
        Document document = documentService.findEntity(documentId);
        documentService.checkOwnership(document, requestingUserId, isAdmin);
        return referenceDocumentMapper.toDtoList(documentReferenceRepository.findByDocument_IdOrderByDateImportAsc(documentId));
    }

    @Transactional
    public void deleteReferenceDocument(UUID documentId, UUID referenceId, UUID requestingUserId, boolean isAdmin) {
        Document document = documentService.findEntity(documentId);
        documentService.checkOwnership(document, requestingUserId, isAdmin);

        DocumentReference reference = documentReferenceRepository.findById(referenceId)
                .orElseThrow(() -> new NotFoundException("REFERENCE_DOCUMENT_NOT_FOUND", "Document de référence introuvable."));
        if (reference.getDocument() == null || !reference.getDocument().getId().equals(documentId)) {
            throw new NotFoundException("REFERENCE_DOCUMENT_NOT_FOUND", "Document de référence introuvable.");
        }

        // document_chunk.id_reference est ON DELETE CASCADE : les chunks
        // indexés sont supprimés automatiquement avec la ligne document_reference.
        documentReferenceRepository.delete(reference);
        objectStorageService.delete(minioProperties.getBucketReferences(), reference.getCheminStockage());
    }

    private void validateFile(MultipartFile file, String fileName) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier envoyé est vide.");
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
