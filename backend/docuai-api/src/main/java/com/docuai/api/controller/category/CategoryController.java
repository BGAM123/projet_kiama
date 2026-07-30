package com.docuai.api.controller.category;

import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.CategoryDTO;
import com.docuai.api.dto.CreateCategoryRequest;
import com.docuai.api.dto.UpdateCategoryRequest;
import com.docuai.api.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture ouverte à tout utilisateur authentifié (pas seulement CATEGORY_MANAGE) :
 * les catégories servent à filtrer/parcourir les Documents Types, une
 * fonctionnalité utilisée par tous les rôles, pas seulement les
 * administrateurs qui les gèrent. Seules les mutations sont réservées à
 * CATEGORY_MANAGE (section 6).
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Référentiel des catégories documentaires")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lister les catégories")
    public ResponseEntity<ApiResponse<List<CategoryDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.listAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Détail d'une catégorie")
    public ResponseEntity<ApiResponse<CategoryDTO>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "Créer une catégorie")
    public ResponseEntity<ApiResponse<CategoryDTO>> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.create(request)));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "Modifier une catégorie")
    public ResponseEntity<ApiResponse<CategoryDTO>> update(@PathVariable UUID id, @RequestBody UpdateCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "Supprimer une catégorie (refusé si un Document Type y est rattaché)")
    public ResponseEntity<ApiResponse<Map<String, String>>> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id.toString())));
    }
}
