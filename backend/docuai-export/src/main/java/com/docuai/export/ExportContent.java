package com.docuai.export;

/**
 * Entrée d'un {@link DocumentExporter}. {@code headerText}/{@code footerText}
 * sont optionnels (texte statique extrait du Document Type source, jamais
 * généré par l'IA — voir {@code DocumentStructure.headerText}/{@code footerText})
 * et peuvent être {@code null}.
 */
public record ExportContent(String title, String content, String headerText, String footerText) {
}
