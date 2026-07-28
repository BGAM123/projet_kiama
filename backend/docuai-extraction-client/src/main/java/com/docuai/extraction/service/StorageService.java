package com.docuai.extraction.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);
    
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:docuai-documents}")
    private String bucketName;

    public StorageService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    /**
     * Upload le fichier sur MinIO et retourne le nom d'objet (ou URL générée/construite).
     */
    public String uploadFile(MultipartFile file) {
        try {
            ensureBucketExists();
            
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
            String fileExtension = originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
            String objectName = UUID.randomUUID().toString() + fileExtension;
            
            try (InputStream is = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(is, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }
            log.info("Fichier {} uploadé avec succès sous {}", originalFilename, objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Erreur lors de l'upload sur MinIO", e);
            throw new RuntimeException("Echec de la sauvegarde du fichier", e);
        }
    }

    public String getFileUrl(String objectName) {
        // Dans un environnement de production, on utiliserait presigned object URL ou un CDN
        // Ici on renvoie simplement une route conceptuelle ou le nom de l'objet
        return bucketName + "/" + objectName;
    }
    
    private void ensureBucketExists() throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket {} créé avec succès", bucketName);
        }
    }
}
