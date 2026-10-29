package com.keystone.fsm.dto.response;

import com.keystone.fsm.domain.model.Attachment;

import java.time.Instant;

public record AttachmentResponse(
    String id,
    String fileName,
    String contentType,
    long sizeBytes,
    String uploadedByName,
    Instant uploadedAt
) {
    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(
            a.getId(), a.getFileName(), a.getContentType(),
            a.getSizeBytes(), a.getUploadedByName(), a.getUploadedAt());
    }
}
