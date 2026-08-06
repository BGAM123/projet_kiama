package com.docuai.api.controller.aiconfig;

import com.docuai.api.dto.AiModelConfigDTO;
import com.docuai.api.dto.ApiResponse;
import com.docuai.api.dto.UpdateAiConfigRequest;
import com.docuai.api.service.AiConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Configuration des fournisseurs IA (Bloc 8) — écran d'administration uniquement. */
@RestController
@RequestMapping("/api/v1/ai-configs")
@Tag(name = "Configurations IA", description = "Fournisseurs/modèles IA disponibles (admin)")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AI_CONFIG_MANAGE')")
    @Operation(summary = "Lister les configurations de fournisseurs IA")
    public ResponseEntity<ApiResponse<List<AiModelConfigDTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(aiConfigService.listAll()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('AI_CONFIG_MANAGE')")
    @Operation(summary = "Modifier une configuration IA (modèle, clé, actif, par défaut)")
    public ResponseEntity<ApiResponse<AiModelConfigDTO>> update(@PathVariable UUID id,
                                                                  @RequestBody UpdateAiConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(aiConfigService.update(id, request)));
    }
}
