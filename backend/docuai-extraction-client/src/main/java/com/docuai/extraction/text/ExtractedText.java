package com.docuai.extraction.text;

/** Résultat d'une extraction Tika : type MIME détecté (magic bytes, pas l'en-tête HTTP client) + texte brut. */
public record ExtractedText(String mimeType, String rawText) {
}
