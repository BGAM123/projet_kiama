package com.docuai.ai.service;

import com.docuai.core.model.SectionConstraints;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Valide le contenu généré d'une section contre ses contraintes
 * ({@link SectionConstraints} : longueur min/max, motif attendu) et son
 * caractère obligatoire — appelé par
 * {@code GenerationStreamService#generateSectionWithRetry} après chaque
 * tentative, pour décider d'un retry avec prompt correctif. Contrairement à
 * {@link StructuralValidator} (vérification a posteriori de l'ensemble du
 * document assemblé, informative), ce validateur est bloquant PAR section et
 * conditionne directement une nouvelle tentative.
 */
@Service
public class SectionConstraintValidator {

    public ValidationResult validate(String content, SectionConstraints constraints, boolean required) {
        List<String> violations = new ArrayList<>();
        String stripped = content == null ? "" : content.strip();

        if (required && stripped.isEmpty()) {
            violations.add("le contenu ne peut pas être vide (section obligatoire)");
            return new ValidationResult(false, violations);
        }
        if (constraints == null) {
            return ValidationResult.ok();
        }
        if (constraints.getMinLength() != null && stripped.length() < constraints.getMinLength()) {
            violations.add("longueur minimale de " + constraints.getMinLength() + " caractères non atteinte (actuelle : " + stripped.length() + ")");
        }
        if (constraints.getMaxLength() != null && stripped.length() > constraints.getMaxLength()) {
            violations.add("longueur maximale de " + constraints.getMaxLength() + " caractères dépassée (actuelle : " + stripped.length() + ")");
        }
        if (constraints.getPattern() != null && !constraints.getPattern().isBlank()) {
            if (!matchesPattern(stripped, constraints.getPattern())) {
                violations.add("ne respecte pas le format attendu (motif : " + constraints.getPattern() + ")");
            }
        }
        return new ValidationResult(violations.isEmpty(), violations);
    }

    private boolean matchesPattern(String content, String pattern) {
        try {
            return Pattern.compile(pattern, Pattern.DOTALL).matcher(content).find();
        } catch (PatternSyntaxException e) {
            // Motif invalide côté configuration du Document Type : ne bloque pas la génération, juste ignoré.
            return true;
        }
    }

    public record ValidationResult(boolean valid, List<String> violations) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
    }
}
