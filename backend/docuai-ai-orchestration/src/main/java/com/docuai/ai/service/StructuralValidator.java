package com.docuai.ai.service;

import com.docuai.core.model.StructureNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vérifie a posteriori que le contenu Markdown généré par le fournisseur IA
 * respecte la structure attendue (titres correspondant aux headings de
 * {@code StructureExtractionService}/édition manuelle, section 4.4).
 * Non bloquant : le résultat sert au Bloc 6 pour décider de redemander une
 * section ({@code docuai.generation.section-retry-attempts}) plutôt qu'à
 * rejeter la réponse.
 */
@Service
public class StructuralValidator {

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);

    public ValidationResult validate(String generatedContent, List<StructureNode> expectedStructure) {
        List<String> expectedHeadings = expectedStructure == null ? List.of() : expectedStructure.stream()
                .filter(n -> "heading".equals(n.getType()))
                .map(StructureNode::getLabel)
                .toList();
        if (expectedHeadings.isEmpty()) {
            return new ValidationResult(true, List.of());
        }
        List<String> foundHeadings = new ArrayList<>();
        Matcher matcher = MD_HEADING.matcher(generatedContent == null ? "" : generatedContent);
        while (matcher.find()) {
            foundHeadings.add(matcher.group(2).strip());
        }
        List<String> missing = expectedHeadings.stream()
                .filter(expected -> foundHeadings.stream().noneMatch(found -> found.equalsIgnoreCase(expected)))
                .toList();
        return new ValidationResult(missing.isEmpty(), missing);
    }

    public record ValidationResult(boolean valid, List<String> missingHeadings) {}
}
