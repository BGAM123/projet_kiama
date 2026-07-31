package com.docuai.api.service;

/**
 * Résultat de {@link FileIngestionService#ingest} : fichier validé, extrait
 * (texte brut Tika) et déjà stocké dans MinIO. {@code content} est conservé
 * en mémoire pour permettre à l'appelant (ex. extraction de structure) de le
 * réutiliser sans re-télécharger depuis MinIO immédiatement après l'upload.
 */
public record StoredFile(String fileName, String objectKey, String mimeType, String rawText, byte[] content) {
}
