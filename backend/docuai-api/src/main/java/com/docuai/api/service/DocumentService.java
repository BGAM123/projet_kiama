package com.docuai.api.service;

import com.docuai.api.dto.CreateDocumentRequest;
import com.docuai.api.dto.DocumentDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentMapper;
import com.docuai.api.mapper.DocumentSectionMapper;
import com.docuai.core.model.Document;
import com.docuai.core.model.DocumentSection;
import com.docuai.core.model.DocumentSectionStatut;
import com.docuai.core.model.DocumentSectionType;
import com.docuai.core.model.DocumentStatut;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Cycle de vie d'un document en édition manuelle assistée : création (à
 * partir du squelette d'un Document Type actif, sections vides), lecture,
 * finalisation/export. L'édition du contenu de chaque section relève de
 * {@link DocumentSectionService} — ce service-ci ne touche pas à {@code
 * user_content}/{@code ai_suggested_content}.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentSectionRepository documentSectionRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final DocumentMapper documentMapper;
    private final DocumentSectionMapper documentSectionMapper;
    private final ExportService exportService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;

    public DocumentService(DocumentRepository documentRepository,
                            DocumentSectionRepository documentSectionRepository,
                            DocumentTypeRepository documentTypeRepository,
                            DocumentStructureRepository documentStructureRepository,
                            DocumentMapper documentMapper,
                            DocumentSectionMapper documentSectionMapper,
                            ExportService exportService,
                            ObjectStorageService objectStorageService,
                            MinioProperties minioProperties) {
        this.documentRepository = documentRepository;
        this.documentSectionRepository = documentSectionRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.documentMapper = documentMapper;
        this.documentSectionMapper = documentSectionMapper;
        this.exportService = exportService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
    }

    @Transactional
    public DocumentDTO create(CreateDocumentRequest request, Utilisateur currentUser) {
        DocumentType documentType = documentTypeRepository.findById(request.getDocumentTypeId())
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));
        DocumentStructure structure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                .orElseThrow(() -> new NotFoundException("STRUCTURE_NOT_FOUND", "Aucune structure trouvée pour ce Document Type."));

        Document document = Document.builder()
                .documentType(documentType)
                .utilisateur(currentUser)
                .statut(DocumentStatut.BROUILLON)
                .build();
        document = documentRepository.save(document);

        List<DocumentSection> sections = new ArrayList<>();
        flatten(structure.getArbreJson(), document, null, sections);
        documentSectionRepository.saveAll(sections);

        return toDto(document, sections);
    }

    @Transactional(readOnly = true)
    public DocumentDTO getById(UUID id, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);
        return toDto(document, documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> listForUser(UUID userId, UUID requestingUserId, boolean isAdmin) {
        if (!isAdmin && !userId.equals(requestingUserId)) {
            throw new AccessDeniedException("Vous ne pouvez consulter que votre propre historique.");
        }
        return documentRepository.findByUtilisateur_IdOrderByDateCreationDesc(userId).stream()
                .map(d -> toDto(d, documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(d.getId())))
                .toList();
    }

    /**
     * Assemble le contenu Markdown final à partir de {@code userContent} de
     * chaque section (sections vides ignorées), exporte en DOCX vers MinIO
     * (même mécanique que l'ancien export automatique de fin de génération
     * IA, réutilisée telle quelle) et marque le document {@code FINALISE}.
     */
    @Transactional
    public DocumentDTO finalizeDocument(UUID id, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        List<DocumentSection> sections = documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id);
        exportToObjectStorage(document, buildExportContent(document, sections));

        document.setStatut(DocumentStatut.FINALISE);
        document.setDateMaj(LocalDateTime.now());
        documentRepository.save(document);

        return toDto(document, sections);
    }

    /**
     * Export à la demande dans le format choisi (DOCX/PDF/Markdown), typiquement
     * juste après la finalisation — indépendant de l'archive DOCX déposée sur
     * MinIO par {@link #finalizeDocument} (qui, elle, sert de lien de
     * téléchargement persistant). Le contenu est réassemblé à la volée depuis
     * les sections, donc un export reflète toujours l'état courant du document.
     */
    @Transactional(readOnly = true)
    public ExportedFile export(UUID id, ExportFormat format, UUID requestingUserId, boolean isAdmin) {
        Document document = findEntity(id);
        checkOwnership(document, requestingUserId, isAdmin);

        List<DocumentSection> sections = documentSectionRepository.findByDocument_IdOrderByOrderIndexAsc(id);
        return exportService.export(buildExportContent(document, sections), format);
    }

    /** En-tête/pied de page repris du Document Type (texte statique extrait du fichier source, jamais généré par l'IA). */
    private ExportContent buildExportContent(Document document, List<DocumentSection> sections) {
        DocumentStructure structure = documentStructureRepository
                .findByDocumentType_Id(document.getDocumentType().getId()).orElse(null);
        return new ExportContent(document.getDocumentType().getNom(), assembleContent(sections),
                structure != null ? structure.getHeaderText() : null,
                structure != null ? structure.getFooterText() : null);
    }

    /** Même convention historique que {@code DocumentTypeExtractionService}/ancien flux : le nœud "cover" n'est pas une section éditable. */
    private void flatten(List<StructureNode> nodes, Document document, DocumentSection parent, List<DocumentSection> out) {
        if (nodes == null) {
            return;
        }
        for (StructureNode node : nodes) {
            if ("cover".equals(node.getType())) {
                continue;
            }
            DocumentSection section = DocumentSection.builder()
                    .document(document)
                    .parentSection(parent)
                    .sourceNodeId(node.getId())
                    .type(mapSectionType(node))
                    .level(node.getLevel())
                    .label(node.getLabel())
                    .tableColumns(resolveTableColumns(node))
                    .orderIndex(out.size())
                    .statut(DocumentSectionStatut.EMPTY)
                    .build();
            out.add(section);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                flatten(node.getChildren(), document, section, out);
            }
        }
    }

    /**
     * Traduit le vocabulaire hérité de {@code StructureNode} ({@code
     * heading}+{@code level}, {@code table}, {@code paragraph_placeholder} —
     * conservé pour ne pas casser l'extraction déterministe/l'éditeur de
     * structure existants) vers le vocabulaire propre du nouveau modèle
     * relationnel ({@link DocumentSectionType}). {@code paragraph}/{@code list}
     * (Document Types importés) deviennent des emplacements à rédiger, comme
     * {@code paragraph_placeholder} (flux IA) — même traitement, un simple
     * slot de texte libre pour l'utilisateur.
     */
    private DocumentSectionType mapSectionType(StructureNode node) {
        String type = node.getType();
        if ("heading".equals(type)) {
            int level = node.getLevel() == null ? 1 : node.getLevel();
            if (level <= 1) return DocumentSectionType.TITLE;
            if (level == 2) return DocumentSectionType.SUBTITLE;
            return DocumentSectionType.SUB_SUBTITLE;
        }
        if ("table".equals(type)) {
            return DocumentSectionType.TABLE;
        }
        return DocumentSectionType.PARAGRAPH_PLACEHOLDER;
    }

    private List<TableColumnDef> resolveTableColumns(StructureNode node) {
        if (!"table".equals(node.getType())) {
            return null;
        }
        if (node.getTableColumns() != null) {
            return node.getTableColumns();
        }
        if (node.getColumns() != null) {
            return node.getColumns().stream().map(name -> TableColumnDef.builder().name(name).build()).toList();
        }
        return null;
    }

    private String assembleContent(List<DocumentSection> sections) {
        StringBuilder sb = new StringBuilder();
        for (DocumentSection section : sections) {
            String content = section.getUserContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(headingPrefix(section.getType())).append(section.getLabel()).append('\n').append(content);
        }
        return sb.toString();
    }

    private String headingPrefix(DocumentSectionType type) {
        return switch (type) {
            case TITLE -> "# ";
            case SUBTITLE -> "## ";
            case SUB_SUBTITLE -> "### ";
            default -> "";
        };
    }

    /** Dégradation volontaire (même choix que l'ancien export automatique) : un échec MinIO ne bloque pas la finalisation, seule l'URL de téléchargement reste absente. */
    private void exportToObjectStorage(Document document, ExportContent exportContent) {
        try {
            ExportedFile file = exportService.export(exportContent, ExportFormat.DOCX);
            String objectKey = "exports/" + document.getId() + "/" + file.filename();
            objectStorageService.upload(minioProperties.getBucketExports(), objectKey, file.content(), file.contentType());
            document.setMinioObjectKey(objectKey);
            document.setExportFormat(ExportFormat.DOCX.name());
        } catch (Exception e) {
            log.warn("Export automatique vers MinIO impossible pour le document {} : {}", document.getId(), e.getMessage());
        }
    }

    /**
     * Moyenne des sections effectivement évaluées — les sections sans score
     * (jamais passées par l'IA, ou réécrites depuis) sont exclues du calcul
     * plutôt que comptées à zéro : un document rédigé à la main afficherait
     * sinon 0 % de confiance, ce qui se lirait comme une alerte alors qu'il n'y
     * a simplement rien à évaluer.
     */
    private Integer averageConfidence(List<DocumentSection> sections) {
        OptionalDouble average = sections.stream()
                .map(DocumentSection::getConfidenceScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average();
        return average.isPresent() ? (int) Math.round(average.getAsDouble()) : null;
    }

    private DocumentDTO toDto(Document document, List<DocumentSection> sections) {
        var dto = documentMapper.toDto(document);
        dto.setSections(documentSectionMapper.toDtoList(sections));
        dto.setGlobalConfidenceScore(averageConfidence(sections));
        if (document.getMinioObjectKey() != null) {
            try {
                dto.setExportUrl(objectStorageService.presignedGetUrl(minioProperties.getBucketExports(), document.getMinioObjectKey()));
            } catch (Exception e) {
                log.warn("URL pré-signée d'export indisponible pour le document {} : {}", document.getId(), e.getMessage());
            }
        }
        return dto;
    }

    Document findEntity(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document introuvable."));
    }

    void checkOwnership(Document document, UUID requestingUserId, boolean isAdmin) {
        UUID ownerId = document.getUtilisateur() != null ? document.getUtilisateur().getId() : null;
        if (!isAdmin && (ownerId == null || !ownerId.equals(requestingUserId))) {
            throw new AccessDeniedException("Ce document ne vous appartient pas.");
        }
    }
}
