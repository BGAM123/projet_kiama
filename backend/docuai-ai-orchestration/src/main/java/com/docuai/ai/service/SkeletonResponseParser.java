package com.docuai.ai.service;

import com.docuai.core.model.StructureNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse la réponse brute du LLM (flux "décrire en texte -> squelette généré
 * par IA", cf. {@link PromptBuilder#buildSkeletonSystemPrompt}) en
 * {@code List<StructureNode>}. Tolérant aux formatages mineurs que les LLM
 * produisent malgré la consigne "réponds uniquement avec le JSON" (bloc de
 * code Markdown ```json ... ```, texte avant/après le tableau).
 */
@Service
public class SkeletonResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[[\\s\\S]*]");

    public List<StructureNode> parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new SkeletonParseException("Réponse vide du fournisseur IA.");
        }
        String candidate = extractJsonArray(rawResponse.strip());
        try {
            return MAPPER.readValue(candidate, new TypeReference<List<StructureNode>>() {});
        } catch (Exception e) {
            throw new SkeletonParseException("Réponse du fournisseur IA non conforme au schéma JSON attendu : " + e.getMessage());
        }
    }

    private String extractJsonArray(String text) {
        Matcher matcher = JSON_ARRAY.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return text;
    }

    public static class SkeletonParseException extends RuntimeException {
        public SkeletonParseException(String message) {
            super(message);
        }
    }
}
