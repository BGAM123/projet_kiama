package com.docuai.api.service;

import com.docuai.api.dto.CategoryDTO;
import com.docuai.api.dto.CreateCategoryRequest;
import com.docuai.api.dto.UpdateCategoryRequest;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.CategoryMapper;
import com.docuai.core.model.Categorie;
import com.docuai.core.repository.CategorieRepository;
import com.docuai.core.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Service applicatif : CRUD des catégories documentaires (section 5). */
@Service
public class CategoryService {

    private final CategorieRepository categorieRepository;
    private final DocumentTypeRepository documentTypeRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(CategorieRepository categorieRepository,
                            DocumentTypeRepository documentTypeRepository,
                            CategoryMapper categoryMapper) {
        this.categorieRepository = categorieRepository;
        this.documentTypeRepository = documentTypeRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO> listAll() {
        return categoryMapper.toDtoList(categorieRepository.findAll());
    }

    @Transactional(readOnly = true)
    public CategoryDTO getById(UUID id) {
        return categoryMapper.toDto(findEntity(id));
    }

    @Transactional
    public CategoryDTO create(CreateCategoryRequest request) {
        Categorie categorie = Categorie.builder()
                .nom(request.getName())
                .description(request.getDescription())
                .build();
        return categoryMapper.toDto(categorieRepository.save(categorie));
    }

    @Transactional
    public CategoryDTO update(UUID id, UpdateCategoryRequest request) {
        Categorie categorie = findEntity(id);
        if (request.getName() != null) categorie.setNom(request.getName());
        if (request.getDescription() != null) categorie.setDescription(request.getDescription());
        return categoryMapper.toDto(categorieRepository.save(categorie));
    }

    /** Une catégorie encore référencée par au moins un Document Type ne peut pas être supprimée. */
    @Transactional
    public void delete(UUID id) {
        Categorie categorie = findEntity(id);
        if (documentTypeRepository.existsByCategorie_Id(categorie.getId())) {
            throw BusinessException.conflict("CATEGORY_IN_USE",
                    "Cette catégorie est utilisée par au moins un Document Type et ne peut pas être supprimée.");
        }
        categorieRepository.deleteById(id);
    }

    private Categorie findEntity(UUID id) {
        return categorieRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Catégorie introuvable."));
    }
}
