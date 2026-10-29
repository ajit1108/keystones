package com.keystone.fsm.service;

import com.keystone.fsm.domain.model.Attachment;
import com.keystone.fsm.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores work-order photos on a configurable volume or in GridFS and never
 * trusts the client-supplied filename for path construction.
 *
 * <p>GridFS is selected with {@code app.uploads.storage=gridfs}; it keeps photos
 * inside MongoDB so they survive redeploys on hosts with ephemeral disks.
 */
@Slf4j
@Service
public class AttachmentService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif");

    static final String STORAGE_FILESYSTEM = "filesystem";
    static final String STORAGE_GRIDFS = "gridfs";

    private final Path root;
    private final long maxBytes;
    private final GridFsTemplate gridFsTemplate;
    private final boolean gridFs;

    public AttachmentService(@Value("${app.uploads.dir:./uploads}") String uploadDir,
                             @Value("${app.uploads.storage:filesystem}") String storage,
                             @Value("${app.uploads.max-bytes:5242880}") long maxBytes,
                             GridFsTemplate gridFsTemplate) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        this.gridFsTemplate = gridFsTemplate;
        this.gridFs = STORAGE_GRIDFS.equalsIgnoreCase(storage);
        if (!gridFs && !STORAGE_FILESYSTEM.equalsIgnoreCase(storage)) {
            log.warn("Unknown app.uploads.storage '{}', falling back to filesystem", storage);
        }
    }

    @PostConstruct
    void init() {
        if (gridFs) {
            log.info("Work-order photos will be stored in MongoDB GridFS");
            return;
        }
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory " + root, e);
        }
    }

    public Attachment store(MultipartFile file, String workOrderId,
                            String userId, String userName) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("No file supplied");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("File exceeds the maximum size of " + (maxBytes / 1024 / 1024) + " MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Only JPEG, PNG, WEBP and GIF images are accepted");
        }

        String id = UUID.randomUUID().toString();
        String relative = workOrderId + "/" + id + EXTENSIONS.get(contentType);
        String storedName = gridFs
                ? storeInGridFs(file, workOrderId, relative, contentType)
                : storeOnDisk(file, workOrderId, relative);

        String displayName = sanitizeFileName(file.getOriginalFilename());

        return Attachment.builder()
                .id(id)
                .fileName(displayName)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .storedName(storedName)
                .storage(gridFs ? STORAGE_GRIDFS : STORAGE_FILESYSTEM)
                .uploadedByUserId(userId)
                .uploadedByName(userName)
                .uploadedAt(Instant.now())
                .build();
    }

    private String storeInGridFs(MultipartFile file, String workOrderId, String relative, String contentType) {
        try (var in = file.getInputStream()) {
            Document metadata = new Document("workOrderId", workOrderId);
            return gridFsTemplate.store(in, relative, contentType, metadata).toHexString();
        } catch (IOException e) {
            log.error("Failed to store attachment for work order {}", workOrderId, e);
            throw new BusinessRuleException("Could not store the uploaded file");
        }
    }

    private String storeOnDisk(MultipartFile file, String workOrderId, String relative) {
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessRuleException("Invalid upload path");
        }
        try {
            Files.createDirectories(target.getParent());
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to store attachment for work order {}", workOrderId, e);
            throw new BusinessRuleException("Could not store the uploaded file");
        }
        return relative;
    }

    public Resource load(Attachment attachment) {
        if (isGridFs(attachment)) {
            var gridFile = gridFsTemplate.findOne(
                    Query.query(Criteria.where("_id").is(objectId(attachment))));
            if (gridFile == null) {
                throw new BusinessRuleException("Attachment file is missing");
            }
            Resource resource = gridFsTemplate.getResource(gridFile);
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessRuleException("Attachment file is missing");
            }
            return resource;
        }

        try {
            Path path = root.resolve(attachment.getStoredName()).normalize();
            if (!path.startsWith(root)) {
                throw new BusinessRuleException("Invalid attachment path");
            }
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessRuleException("Attachment file is missing");
            }
            return resource;
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read attachment");
        }
    }

    public void delete(Attachment attachment) {
        if (isGridFs(attachment)) {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(objectId(attachment))));
            return;
        }
        try {
            Files.deleteIfExists(root.resolve(attachment.getStoredName()).normalize());
        } catch (IOException e) {
            log.warn("Could not delete attachment file {}", attachment.getStoredName(), e);
        }
    }

    private boolean isGridFs(Attachment attachment) {
        return STORAGE_GRIDFS.equals(attachment.getStorage());
    }

    private ObjectId objectId(Attachment attachment) {
        try {
            return new ObjectId(attachment.getStoredName());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid attachment reference");
        }
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "photo";
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
