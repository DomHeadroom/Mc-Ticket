package it.domheadroom.mc_ticket.dto;

import it.domheadroom.mc_ticket.entity.Attachment;

import java.util.UUID;

public record AttachmentResponse(
    UUID id,
    String fileName,
    Long fileSizeBytes,
    String mimeType
) {
    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(a.getId(), a.getFileName(), a.getFileSizeBytes(), a.getMimeType());
    }
}
