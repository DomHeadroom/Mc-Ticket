package it.domheadroom.mc_ticket.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final S3Client s3Client;
    private final String bucket;
    private final long maxFileSize;

    public FileStorageService(
            S3Client s3Client,
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.max-file-size:10485760}") long maxFileSize) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.maxFileSize = maxFileSize;
    }

    @PostConstruct
    public void init() {
        for (int i = 1; i <= 10; i++) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                log.info("Created S3 bucket: {}", bucket);
                return;
            } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException e) {
                log.info("Bucket {} already exists", bucket);
                return;
            } catch (Exception e) {
                if (i < 10) {
                    log.warn("S3 not ready yet (attempt {}/10), retrying in 2s...", i);
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                } else {
                    log.error("S3 not available after 10 attempts", e);
                    throw new RuntimeException("S3 not available: " + e.getMessage(), e);
                }
            }
        }
    }

    public String store(MultipartFile file) throws IOException {
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "File exceeds maximum allowed size of " + maxFileSize + " bytes");
        }

        var originalName = file.getOriginalFilename();
        var ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
            ext = ext.replaceAll("[^a-zA-Z0-9._-]", "");
        }
        var objectKey = UUID.randomUUID() + ext;

        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(file.getContentType())
                        .contentLength(file.getSize())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return objectKey;
    }

    public InputStream load(String objectKey) {
        return s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build());
    }

    public void delete(String objectKey) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .build());
    }
}
