package com.docuai.api.util;

/** Petits utilitaires de noms de fichiers partagés par FileIngestionService et DocumentTypeExtractionService. */
public final class FileNames {

    private FileNames() {
    }

    /** Extension en minuscules sans le point (ex. "docx"), chaîne vide si absente. */
    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
    }

    /** Retire tout séparateur de chemin et caractère non sûr pour une clé d'objet S3/MinIO. */
    public static String sanitize(String rawName) {
        String name = (rawName == null || rawName.isBlank()) ? "document" : rawName;
        // Ne garde que le nom de fichier final (ignore tout chemin envoyé par
        // certains navigateurs/proxys) puis remplace les caractères non sûrs.
        String base = name.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
