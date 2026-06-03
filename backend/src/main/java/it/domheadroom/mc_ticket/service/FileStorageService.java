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

    public FileStorageService(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    public String store(MultipartFile file) throws IOException {
        var originalName = file.getOriginalFilename();
        var ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        var storedName = UUID.randomUUID() + ext;
        var target = uploadDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return storedName;
    }

    public Path load(String storedName) {
        return uploadDir.resolve(storedName);
    }

    public void delete(String storedName) throws IOException {
        Files.deleteIfExists(uploadDir.resolve(storedName));
    }
}
