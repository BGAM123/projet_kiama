package com.docuai.ai.service;

import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.StructureNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Construit les prompts envoyés au fournisseur IA (section 4.3/4.4) à partir
 * de la structure attendue du Document Type ciblé (contrainte de forme en
 * sortie, cf. {@link StructuralValidator}), des chunks de documents de
 * référence retenus par le RAG ({@code SimilaritySearchService}) et des
 * consignes utilisateur (langue, ton, longueur cible).
 */
@Service
public class PromptBuilder {

    public String buildSystemPrompt(List<StructureNode> expectedStructure, String language, String tone, String targetLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant de rédaction de documents professionnels. ");
        sb.append("Réponds exclusivement dans la langue suivante : ").append(language == null ? "FR" : language).append(". ");
        sb.append("Adopte un ton ").append(tone == null ? "neutre" : tone.toLowerCase()).append(". ");
        if (targetLength != null) {
            sb.append("Vise une longueur de contenu ").append(describeTargetLength(targetLength)).append(". ");
        }
        if (expectedStructure != null && !expectedStructure.isEmpty()) {
            sb.append("\n\nStructure attendue du document (respecte l'ordre et les niveaux de titres, format Markdown) :\n");
            sb.append(renderStructureBlock(expectedStructure));
        }
        return sb.toString();
    }

    public String buildUserPrompt(String userInstructions, List<DocumentChunk> referenceChunks) {
        if (referenceChunks == null || referenceChunks.isEmpty()) {
            return userInstructions;
        }
        String context = referenceChunks.stream()
                .map(DocumentChunk::getContenu)
                .collect(Collectors.joining("\n---\n"));
        return "Extraits de documents de référence à utiliser comme source :\n" + context
                + "\n\nInstructions :\n" + userInstructions;
    }

    private String describeTargetLength(String targetLength) {
        return switch (targetLength.toUpperCase()) {
            case "COURT" -> "courte (quelques paragraphes)";
            case "MOYEN" -> "moyenne (une à deux pages)";
            case "LONG" -> "longue (plusieurs pages)";
            case "EXTENSIF" -> "exhaustive (couvre tous les aspects en détail)";
            default -> "adaptée au contexte";
        };
    }

    /**
     * Rendu textuel de l'arbre de structure (titres Markdown `#`..`###` par
     * niveau, tableaux signalés entre crochets) — extrait de
     * {@link #buildSystemPrompt} pour être réutilisable telle quelle par
     * {@code ConversationService} (Bloc 6, chat), qui construit son propre
     * préambule mais veut ancrer ses réponses sur la même structure attendue
     * que la génération.
     */
    public String renderStructureBlock(List<StructureNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (StructureNode node : nodes) {
            if ("heading".equals(node.getType())) {
                sb.append("#".repeat(node.getLevel() == null ? 1 : node.getLevel())).append(' ').append(node.getLabel()).append('\n');
            } else if ("table".equals(node.getType())) {
                sb.append("[Tableau : ").append(node.getLabel()).append("]\n");
            }
        }
        return sb.toString();
    }
}
