package com.docuai.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Enveloppe JSON top-niveau attendue du LLM pour le flux "Générer avec l'IA" d'un document — voir {@link GeneratedSectionContent}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentContentResponse {
    private List<GeneratedSectionContent> sections;
}
