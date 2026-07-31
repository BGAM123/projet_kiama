package com.docuai.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Liaison de docuai.upload.* (application.yml / variables d'environnement). */
@ConfigurationProperties(prefix = "docuai.upload")
@Getter
@Setter
public class UploadProperties {
    private int maxSizeMb = 25;
    private List<String> allowedMimeTypes = List.of();
}
