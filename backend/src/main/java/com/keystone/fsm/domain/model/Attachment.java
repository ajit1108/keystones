package com.keystone.fsm.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Embedded sub-document describing a photo attached to a work order.
 * The binary lives on disk or in GridFS; storedName is never exposed through the API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    private String id;
    private String fileName;
    private String contentType;
    private long sizeBytes;
    /** Relative path on the upload volume, or the GridFS file id. */
    private String storedName;
    /** filesystem | gridfs — null on legacy documents means filesystem. */
    private String storage;
    private String uploadedByUserId;
    private String uploadedByName;
    private Instant uploadedAt;
}
