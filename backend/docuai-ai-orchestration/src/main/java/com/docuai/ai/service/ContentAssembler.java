package com.docuai.ai.service;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Assemble les sections générées section par section (SSE, Bloc 6) en un
 * unique contenu Markdown — alimente {@code document_genere.contenu} (section
 * 4.4 : rendu texte consommé directement par docuai-export sans reparser le
 * JSON pivot).
 */
@Service
public class ContentAssembler {

    public String assemble(List<GeneratedSection> sections) {
        return sections.stream()
                .sorted(Comparator.comparingInt(GeneratedSection::index))
                .map(GeneratedSection::content)
                .collect(Collectors.joining("\n\n"));
    }

    public record GeneratedSection(int index, String label, String content) {}
}
