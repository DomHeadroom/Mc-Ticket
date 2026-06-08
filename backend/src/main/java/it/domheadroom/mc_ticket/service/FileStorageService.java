package it.domheadroom.mc_ticket.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path uploadDir;
    private final long maxFileSize;

    public FileStorageService(
            @Value("${app.storage.upload-dir}") String uploadDir,
            @Value("${app.storage.max-file-size:10485760}") long maxFileSize
    ) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "File exceeds maximum allowed size of " + maxFileSize + " bytes"
            );
        }

        var originalName = file.getOriginalFilename();
        var ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
            ext = ext.replaceAll("[^a-zA-Z0-9._-]", "");
        }
        var storedName = UUID.randomUUID() + ext;
        var target = uploadDir.resolve(storedName).normalize();

        if (!target.startsWith(uploadDir)) {
            throw new IOException("Path traversal detected");
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    public Path load(String storedName) {
        var target = uploadDir.resolve(storedName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Path traversal detected");
        }
        return target;
    }

    public void delete(String storedName) throws IOException {
        var target = uploadDir.resolve(storedName).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Path traversal detected");
        }
        Files.deleteIfExists(target);
    }
}
