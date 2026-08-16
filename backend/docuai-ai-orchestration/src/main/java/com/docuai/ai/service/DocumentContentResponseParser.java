package com.docuai.ai.service;

import com.docuai.ai.dto.DocumentContentResponse;
import com.docuai.ai.dto.GeneratedSectionContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse la réponse brute du LLM pour le flux "Générer avec l'IA" d'un document
 * (cf. {@link PromptBuilder#buildDocumentContentSystemPrompt}) en {@code
 * List<GeneratedSectionContent>}. Même tolérance que {@link
 * SkeletonResponseParser} (bloc de code Markdown ```json ... ```, texte
 * parasite autour de l'objet) — contrairement à {@link
 * SectionImprovementResponseParser}, une réponse non conforme est ici une
 * vraie erreur : il n'y a pas de repli en texte brut possible pour un objet
 * censé couvrir plusieurs sections identifiées par {@code id}.
 */
@Service
public class DocumentContentResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[\\s\\S]*}");

    public List<GeneratedSectionContent> parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new DocumentContentParseException("Réponse vide du fournisseur IA.");
        }
        String candidate = extractJsonObject(rawResponse.strip());
        DocumentContentResponse parsed;
        try {
            parsed = MAPPER.readValue(candidate, DocumentContentResponse.class);
        } catch (Exception e) {
            throw new DocumentContentParseException("Réponse du fournisseur IA non conforme au schéma JSON attendu : " + e.getMessage());
        }
        if (parsed.getSections() == null) {
            throw new DocumentContentParseException("Réponse du fournisseur IA sans champ \"sections\".");
        }
        return parsed.getSections();
    }

    private String extractJsonObject(String text) {
        Matcher matcher = JSON_OBJECT.matcher(text);
        return matcher.find() ? matcher.group() : text;
    }

    public static class DocumentContentParseException extends RuntimeException {
        public DocumentContentParseException(String message) {
            super(message);
        }
    }
}
