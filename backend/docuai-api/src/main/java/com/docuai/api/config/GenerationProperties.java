package com.docuai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Liaison de docuai.generation.* (application.yml). */
@ConfigurationProperties(prefix = "docuai.generation")
@Getter
@Setter
public class GenerationProperties {
    private int maxReferenceDocuments = 10;
    private int sectionRetryAttempts = 2;
}
