package com.docuai.export;

/** Fichier binaire produit par un {@link DocumentExporter}. */
public record ExportedFile(byte[] content, String filename, String contentType) {
}
