package com.docuai.api.service;

import com.docuai.api.dto.DocumentStructureDTO;
import com.docuai.api.dto.DocumentTypeDTO;
import com.docuai.api.dto.UpdateDocumentTypeRequest;
import com.docuai.api.dto.UpdateStructureRequest;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.DocumentStructureMapper;
import com.docuai.api.mapper.DocumentTypeMapper;
import com.docuai.api.mapper.StructureNodeMapper;
import com.docuai.core.model.Categorie;
import com.docuai.core.model.DocumentStructure;
import com.docuai.core.model.DocumentType;
import com.docuai.core.model.DocumentTypeStatut;
import com.docuai.core.repository.CategorieRepository;
import com.docuai.core.repository.DocumentStructureRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service applicatif : CRUD référentiel des Documents Types (section 5) +
 * consultation/correction de leur structure. L'import de fichier, le
 * déclenchement d'extraction et les transitions de statut associées
 * relèvent du Bloc 4 (pipeline d'extraction), pas de ce service.
 */
@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;
    private final DocumentStructureRepository documentStructureRepository;
    private final CategorieRepository categorieRepository;
    private final DocumentTypeMapper documentTypeMapper;
    private final DocumentStructureMapper documentStructureMapper;
    private final StructureNodeMapper structureNodeMapper;

    public DocumentTypeService(DocumentTypeRepository documentTypeRepository,
                                DocumentStructureRepository documentStructureRepository,
                                CategorieRepository categorieRepository,
                                DocumentTypeMapper documentTypeMapper,
                                DocumentStructureMapper documentStructureMapper,
                                StructureNodeMapper structureNodeMapper) {
        this.documentTypeRepository = documentTypeRepository;
        this.documentStructureRepository = documentStructureRepository;
        this.categorieRepository = categorieRepository;
        this.documentTypeMapper = documentTypeMapper;
        this.documentStructureMapper = documentStructureMapper;
        this.structureNodeMapper = structureNodeMapper;
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeDTO> listAll(UUID categoryId, DocumentTypeStatut status) {
        List<DocumentType> results;
        if (categoryId != null && status != null) {
            results = documentTypeRepository.findByCategorie_IdAndStatut(categoryId, status);
        } else if (categoryId != null) {
            results = documentTypeRepository.findByCategorie_Id(categoryId);
        } else if (status != null) {
            results = documentTypeRepository.findByStatut(status);
        } else {
            results = documentTypeRepository.findAll();
        }
        return documentTypeMapper.toDtoList(results);
    }

    @Transactional(readOnly = true)
    public DocumentTypeDTO getById(UUID id) {
        return documentTypeMapper.toDto(findEntity(id));
    }

    @Transactional
    public DocumentTypeDTO update(UUID id, UpdateDocumentTypeRequest request) {
        DocumentType documentType = findEntity(id);
        if (request.getName() != null) documentType.setNom(request.getName());
        if (request.getDescription() != null) documentType.setDescription(request.getDescription());
        if (request.getCategoryId() != null) documentType.setCategorie(resolveCategory(request.getCategoryId()));
        return documentTypeMapper.toDto(documentTypeRepository.save(documentType));
    }

    /**
     * Archivage logique (statut -> ARCHIVE) plutôt que suppression physique —
     * un Document Type archivé reste consultable dans l'historique des
     * documents déjà générés à partir de lui (cf. deleteDocumentType côté
     * frontend, qui applique la même sémantique).
     */
    @Transactional
    public void archive(UUID id) {
        DocumentType documentType = findEntity(id);
        documentType.setStatut(DocumentTypeStatut.ARCHIVE);
        documentTypeRepository.save(documentType);
    }

    @Transactional(readOnly = true)
    public DocumentStructureDTO getStructure(UUID documentTypeId) {
        return documentStructureMapper.toDto(findStructureEntity(documentTypeId));
    }

    @Transactional
    public DocumentStructureDTO updateStructure(UUID documentTypeId, UpdateStructureRequest request) {
        DocumentStructure structure = findStructureEntity(documentTypeId);
        structure.setArbreJson(structureNodeMapper.toEntityList(request.getTree()));
        return documentStructureMapper.toDto(documentStructureRepository.save(structure));
    }

    private DocumentStructure findStructureEntity(UUID documentTypeId) {
        return documentStructureRepository.findByDocumentType_Id(documentTypeId)
                .orElseThrow(() -> new NotFoundException("STRUCTURE_NOT_FOUND",
                        "Aucune structure trouvée pour ce Document Type."));
    }

    private Categorie resolveCategory(UUID categoryId) {
        return categorieRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Catégorie introuvable : " + categoryId));
    }

    private DocumentType findEntity(UUID id) {
        return documentTypeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("DOCUMENT_TYPE_NOT_FOUND", "Document Type introuvable."));
    }
}
