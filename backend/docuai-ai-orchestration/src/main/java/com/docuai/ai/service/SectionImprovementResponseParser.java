package com.docuai.ai.service;

import com.docuai.ai.dto.SectionImprovement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse la réponse du bouton "Améliorer avec l'IA"
 * ({@link PromptBuilder#buildSectionImprovementSystemPrompt}), qui demande un
 * objet JSON {@code {"content": "...", "confidence": 0-100}}.
 *
 * <p>Même parti pris de tolérance que {@link SkeletonResponseParser} (bloc de
 * code Markdown, texte autour du JSON), avec une différence importante : ici
 * une réponse non conforme n'est <b>pas</b> une erreur. Le texte brut reste une
 * suggestion parfaitement utilisable — on le retourne tel quel, simplement sans
 * score de confiance, plutôt que de faire échouer l'amélioration pour un
 * problème de format.</p>
 */
@Service
public class SectionImprovementResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    public SectionImprovement parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return SectionImprovement.builder().content("").build();
        }
        String raw = rawResponse.strip();
        Matcher matcher = JSON_OBJECT.matcher(raw);
        if (matcher.find()) {
            try {
                JsonNode node = MAPPER.readTree(matcher.group());
                JsonNode content = node.get("content");
                if (content != null && content.isTextual() && !content.asText().isBlank()) {
                    return SectionImprovement.builder()
                            .content(content.asText().strip())
                            .confidence(readConfidence(node.get("confidence")))
                            .build();
                }
            } catch (Exception e) {
                // Format non respecté : on retombe sur le texte brut ci-dessous.
            }
        }
        return SectionImprovement.builder().content(raw).build();
    }

    /** Accepte un nombre (85) comme une chaîne ("85"), et borne à [0, 100] — la colonne SQL porte la même contrainte. */
    private Double readConfidence(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        double value;
        if (node.isNumber()) {
            value = node.asDouble();
        } else if (node.isTextual()) {
            try {
                value = Double.parseDouble(node.asText().strip().replace("%", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        } else {
            return null;
        }
        return Math.max(0d, Math.min(100d, value));
    }
}
