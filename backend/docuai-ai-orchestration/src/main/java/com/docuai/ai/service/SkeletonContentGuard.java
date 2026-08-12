package com.docuai.ai.service;

import com.docuai.core.model.StructureNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristique de garde-fou du flux "décrire en texte -> squelette généré par
 * IA" (Document Type) : détecte quand le LLM a produit du contenu rédigé
 * (phrases complètes, longs paragraphes) au lieu d'un simple squelette de
 * titres/sous-titres/tableaux, malgré la consigne de
 * {@link PromptBuilder#buildSkeletonSystemPrompt}. Non bloquant à lui seul —
 * {@code DocumentTypeGenerationService} l'utilise pour décider d'un retry
 * avec prompt correctif (même logique que {@link SectionConstraintValidator}
 * pour la génération de contenu).
 */
@Service
public class SkeletonContentGuard {

    private static final int MAX_LABEL_LENGTH = 80;
    private static final int MAX_LABEL_WORDS = 12;
    private static final Set<String> ALLOWED_TYPES = Set.of("heading", "table", "paragraph_placeholder");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]\\s");

    public ValidationResult validate(List<StructureNode> tree) {
        List<String> violations = new ArrayList<>();
        if (tree == null || tree.isEmpty()) {
            violations.add("le squelette généré est vide");
            return new ValidationResult(false, violations);
        }
        checkNodes(tree, violations);
        return new ValidationResult(violations.isEmpty(), violations);
    }

    private void checkNodes(List<StructureNode> nodes, List<String> violations) {
        for (StructureNode node : nodes) {
            checkNode(node, violations);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                checkNodes(node.getChildren(), violations);
            }
        }
    }

    private void checkNode(StructureNode node, List<String> violations) {
        String type = node.getType();
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            violations.add("type de section inattendu (\"" + type + "\") pour le label \"" + node.getLabel() + "\" — attendu : heading, table ou paragraph_placeholder");
            return;
        }
        String label = node.getLabel() == null ? "" : node.getLabel().strip();
        if (label.isEmpty()) {
            violations.add("une section n'a pas de label");
            return;
        }
        if (looksLikeWrittenContent(label)) {
            violations.add("le label \"" + truncate(label) + "\" ressemble à du contenu rédigé plutôt qu'à un titre de section");
        }
    }

    private boolean looksLikeWrittenContent(String label) {
        if (label.length() > MAX_LABEL_LENGTH) {
            return true;
        }
        int wordCount = label.split("\\s+").length;
        return wordCount > MAX_LABEL_WORDS && SENTENCE_END.matcher(label).find();
    }

    private String truncate(String label) {
        return label.length() <= 60 ? label : label.substring(0, 60) + "…";
    }

    /** Complément de prompt injecté lors d'une nouvelle tentative après violation détectée. */
    public String buildCorrectivePrompt(List<String> violations) {
        return "\n\nTa réponse précédente ne respectait pas la consigne : " + String.join(" ; ", violations)
                + ". Rappel : chaque \"label\" doit être un intitulé court (quelques mots maximum), jamais une phrase ou un paragraphe rédigé. Régénère uniquement le squelette JSON conforme au schéma demandé.";
    }

    public record ValidationResult(boolean valid, List<String> violations) {}
}
