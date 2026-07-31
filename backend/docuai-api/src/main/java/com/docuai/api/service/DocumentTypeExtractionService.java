package com.docuai.api.service;

import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentTypeMapper;
import com.docuai.api.util.FileNames;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.Utilisateur;
import com.docuai.core.repository.CategorieRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import com.docuai.extraction.config.MinioProperties;
import com.docuai.extraction.storage.ObjectStorageService;
import com.docuai.extraction.structure.StructureExtractionService;
import com.docuai.extraction.text.ExtractedText;
import com.docuai.extraction.text.TextExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestration Bloc 4 : import d'un fichier source -> création du Document
 * Type -> extraction de sa structure -> persistance, ainsi que la
 * ré-extraction (relance après échec ou correction du fichier source).
 * DocumentTypeService (Bloc 3) reste responsable du CRUD référentiel pur
 * (nom/description/catégorie), de l'archivage et désormais de la validation
 * (activation) ; ce service-ci ne gère que le cycle piloté par l'extraction
 * elle-même (IMPORTE/EN_EXTRACTION -> STRUCTURE_EXTRAITE|ECHEC_EXTRACTION).
 * <p>
 * NB : {@code resilience4j.retry.instances.extraction} est déjà configuré
 * (application.yml) mais n'est pas branché ici — {@code @Retry} nécessite un
 * appel via le proxy Spring (donc une méthode publique appelée depuis un
 * *autre* bean), ce qui demanderait d'extraire runExtraction dans un
 * collaborateur dédié. Laissé pour une itération ultérieure plutôt que
 * d'ajouter une annotation silencieusement inopérante.
 */
@Service
public class DocumentTypeExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeExtractionService.class);

    private final FileIngestionService fileIngestionService;
    private final StructureExtractionService structureExtractionService;
    private final TextExtractionService textExtractionService;
    private final ObjectStorageService objectStorageService;
    private final MinioProperties minioProperties;
    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final CategorieRepository categorieRepository;
    private final DocumentTypeMapper documentTypeMapper;

    public DocumentTypeExtractionService(FileIngestionService fileIngestionService,
                                          StructureExtractionService structureExtractionService,
                                          TextExtractionService textExtractionService,
                                          ObjectStorageService objectStorageService,
                                          MinioProperties minioProperties,
                                          DocumentTypeRepository documentTypeRepository,
                                          DocumentStructureRepository documentStructureRepository,
                                          CategorieRepository categorieRepository,
                                          DocumentTypeMapper documentTypeMapper) {
        this.fileIngestionService = fileIngestionService;
        this.structureExtractionService = structureExtractionService;
        this.textExtractionService = textExtractionService;
        this.objectStorageService = objectStorageService;
        this.minioProperties = minioProperties;
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.categorieRepository = categorieRepository;
        this.documentTypeMapper = documentTypeMapper;
    }

    @Transactional
    public DocumentTypeDTO importAndExtract(MultipartFile file, String name, String description, UUID categoryId, Utilisateur currentUser) {
        Categorie categorie = categorieRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Catégorie introuvable : " + categoryId));

        StoredFile stored = fileIngestionService.ingest(file);

        DocumentType documentType = DocumentType.builder()
                .nom(name)
                .description(description)
                .categorie(categorie)
                .statut(DocumentTypeStatut.EN_EXTRACTION)
                .version(1)
                .utilisateurCreateur(currentUser)
                .fichierSourceCle(stored.objectKey())
                .build();
        documentType = documentTypeRepository.save(documentType);

        runExtraction(documentType, stored.content(), FileNames.extensionOf(stored.fileName()), stored.rawText());

        return documentTypeMapper.toDto(documentTypeRepository.save(documentType));
    }

    @Transactional
    public DocumentTypeDTO reextract(UUID documentTypeId) {
        DocumentType documentType = documentTypeRepository.findById(documentTypeId)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));
        if (documentType.getFichierSourceCle() == null) {
            throw new IllegalArgumentException("Aucun fichier source associé à ce Document Type — impossible de relancer l'extraction.");
        }

        byte[] content = objectStorageService.download(minioProperties.getBucketSources(), documentType.getFichierSourceCle());
        ExtractedText extractedText = textExtractionService.extract(content);

        documentType.setStatut(DocumentTypeStatut.EN_EXTRACTION);
        runExtraction(documentType, content, FileNames.extensionOf(documentType.getFichierSourceCle()), extractedText.rawText());

        return documentTypeMapper.toDto(documentTypeRepository.save(documentType));
    }

    /** Construit l'arbre (préfixé d'un nœud "cover") et fait transitionner le statut selon le résultat. */
    private void runExtraction(DocumentType documentType, byte[] content, String extension, String fallbackRawText) {
        try {
            List<StructureNode> tree = new ArrayList<>();
            tree.add(StructureNode.builder().id("cover").type("cover").label(documentType.getNom()).build());
            tree.addAll(structureExtractionService.extract(content, extension, fallbackRawText));

            boolean hasToc = tree.stream().anyMatch(n -> "heading".equals(n.getType()) && n.getLevel() != null && n.getLevel() == 1);

            DocumentStructure structure = documentStructureRepository.findByDocumentType_Id(documentType.getId())
                    .orElseGet(() -> DocumentStructure.builder().documentType(documentType).build());
            structure.setArbreJson(tree);
            structure.setPossedeToc(hasToc);
            documentStructureRepository.save(structure);

            documentType.setStatut(DocumentTypeStatut.STRUCTURE_EXTRAITE);
        } catch (Exception e) {
            // Volontairement large (pas seulement StructureExtractionException) :
            // toute panne à cette étape (parsing POI/PDFBox comme persistance de
            // la DocumentStructure) doit dégrader l'état vers ECHEC_EXTRACTION
            // plutôt que de laisser l'exception remonter jusqu'au contrôleur et
            // provoquer un 500 générique sur /import — l'import du fichier a
            // réussi (DocumentType déjà persisté), seule l'extraction de
            // structure a échoué, ce que le statut ECHEC_EXTRACTION exprime déjà.
            log.warn("Échec d'extraction de structure pour le document type {} : {}", documentType.getId(), e.getMessage(), e);
            documentType.setStatut(DocumentTypeStatut.ECHEC_EXTRACTION);
        }
    }
}
