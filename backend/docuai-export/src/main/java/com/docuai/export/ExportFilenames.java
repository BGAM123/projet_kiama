package com.docuai.export;

import java.text.Normalizer;
import java.util.regex.Pattern;

/** Construit un nom de fichier sûr à partir du titre saisi par l'utilisateur. */
final class ExportFilenames {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private ExportFilenames() {
    }

    static String build(String title, String extension) {
        String base = (title == null || title.isBlank()) ? "document" : title;
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = NON_SLUG.matcher(normalized.toLowerCase()).replaceAll("-").replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "document";
        }
        return slug + "." + extension;
    }
}
