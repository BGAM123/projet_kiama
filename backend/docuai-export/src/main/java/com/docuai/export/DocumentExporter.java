package com.docuai.export;

/** Stratégie d'export vers un format cible (DOCX/PDF/Markdown). */
public interface DocumentExporter {

    ExportFormat supportedFormat();

    ExportedFile export(ExportContent request);
}
