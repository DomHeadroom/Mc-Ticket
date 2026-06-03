package it.domheadroom.mc_ticket.dto;

import it.domheadroom.mc_ticket.entity.ImportStatus;

import java.util.UUID;

public record BulkImportResponse(
    UUID id,
    String fileName,
    String fileFormat,
    int totalRows,
    int processedRows,
    int failedRows,
    ImportStatus status
) {}
