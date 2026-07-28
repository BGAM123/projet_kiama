package com.docuai.api.controller.ai;

import com.docuai.ai.dto.AiRequest;
import com.docuai.ai.dto.AiResponse;
import com.docuai.ai.service.AiOrchestratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiOrchestratorService aiOrchestratorService;

    public AiController(AiOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    @PostMapping("/generate")
    public ResponseEntity<AiResponse> generateText(@RequestBody AiRequest request) {
        AiResponse response = aiOrchestratorService.generate(request);
        return ResponseEntity.ok(response);
    }
}
