package com.docuai.extraction.structure;

/** Échec de construction de l'arbre de structure — traduit en 400 côté docuai-api. */
public class StructureExtractionException extends RuntimeException {
    public StructureExtractionException(String message) {
        super(message);
    }

    public StructureExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
