package com.docuai.extraction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Liaison de docuai.minio.* (application.yml / variables d'environnement). */
@ConfigurationProperties(prefix = "docuai.minio")
@Getter
@Setter
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private long presignedUrlTtlSeconds = 900;
    private String bucketSources;
    private String bucketReferences;
    private String bucketExports;
}
