package com.docuai.ai.port;

import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.enums.AiProvider;
import reactor.core.publisher.Flux;

/**
 * Contrat commun à tous les fournisseurs IA (Pattern Stratégie, section 4.3).
 * {@link #provider()} permet à {@code AiProviderFactory} de construire sa table
 * de résolution par simple injection de {@code List<AiProviderPort>} — les
 * adaptateurs annotés {@code @Component} sont auto-découverts par Spring, ceux
 * qui ne le sont pas (fournisseurs "structurés mais non branchés par défaut")
 * n'apparaissent tout simplement pas dans cette liste.
 */
public interface AiProviderPort {

    AiProvider provider();

    GenerationResult generate(GenerationRequest request);

    Flux<Chunk> streamGenerate(GenerationRequest request);
}
