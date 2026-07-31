package com.docuai.extraction.storage;

import com.docuai.extraction.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Fine couche au-dessus du SDK MinIO : upload d'un objet (en s'assurant que
 * le bucket existe — le sidecar {@code minio-init} du docker-compose le fait
 * déjà en dev, mais on reste défensif si MinIO tourne sans lui) et génération
 * d'une URL de téléchargement pré-signée (TTL configurable, section 9).
 */
@Service
public class ObjectStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public ObjectStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /** Upload les octets fournis sous {@code objectKey} dans {@code bucket}, en créant le bucket si besoin. */
    public void upload(String bucket, String objectKey, byte[] content, String contentType) {
        try {
            ensureBucket(bucket);
            try (InputStream in = new ByteArrayInputStream(content)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(in, content.length, -1)
                        .contentType(contentType)
                        .build());
            }
        } catch (Exception e) {
            throw new ObjectStorageException("Échec de l'envoi du fichier vers le stockage objet (MinIO).", e);
        }
    }

    /** Retélécharge intégralement un objet précédemment stocké (ex. ré-extraction, cf. DocumentTypeExtractionService). */
    public byte[] download(String bucket, String objectKey) {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new ObjectStorageException("Échec du téléchargement du fichier source depuis le stockage objet (MinIO).", e);
        }
    }

    /** URL de téléchargement temporaire (TTL = docuai.minio.presigned-url-ttl-seconds). */
    public String presignedGetUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry((int) properties.getPresignedUrlTtlSeconds())
                    .build());
        } catch (Exception e) {
            throw new ObjectStorageException("Échec de la génération de l'URL de téléchargement (MinIO).", e);
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
