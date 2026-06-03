package it.domheadroom.mc_ticket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.domheadroom.mc_ticket.dto.BulkImportResponse;
import it.domheadroom.mc_ticket.dto.CreateTicketRequest;
import it.domheadroom.mc_ticket.dto.TicketResponse;
import it.domheadroom.mc_ticket.entity.*;
import it.domheadroom.mc_ticket.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final BulkImportRepository bulkImportRepository;
    private final FileStorageService fileStorageService;
    private final NlpService nlpService;
    private final ObjectMapper objectMapper;

    public TicketService(TicketRepository ticketRepository,
                         CategoryRepository categoryRepository,
                         AttachmentRepository attachmentRepository,
                         BulkImportRepository bulkImportRepository,
                         FileStorageService fileStorageService,
                         NlpService nlpService) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.attachmentRepository = attachmentRepository;
        this.bulkImportRepository = bulkImportRepository;
        this.fileStorageService = fileStorageService;
        this.nlpService = nlpService;
        this.objectMapper = new ObjectMapper();
    }

    public TicketResponse createTicket(CreateTicketRequest req, User requester) {
        return createTicket(req, requester, null);
    }

    public TicketResponse createTicket(CreateTicketRequest req, User requester, MultipartFile attachment) {
        var ticket = new Ticket();
        ticket.setTitle(req.title());
        ticket.setDescription(req.description());
        ticket.setRequester(requester);
        ticket.setSource("manual");
        ticket.setNlpProcessed(false);

        if (req.categorySlug() != null && !req.categorySlug().isBlank()) {
            categoryRepository.findBySlug(req.categorySlug()).ifPresent(ticket::setCategoryIdUser);
        }

        if (req.urgencyReported() != null && !req.urgencyReported().isBlank()) {
            try {
                ticket.setUrgencyReported(UrgencyLevel.valueOf(req.urgencyReported()));
            } catch (IllegalArgumentException e) {
                ticket.setUrgencyReported(UrgencyLevel.medium);
            }
        } else {
            ticket.setUrgencyReported(UrgencyLevel.medium);
        }

        if (req.openedAt() != null && !req.openedAt().isBlank()) {
            try {
                ticket.setOpenedAt(OffsetDateTime.parse(req.openedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            } catch (DateTimeParseException e) {
                ticket.setOpenedAt(OffsetDateTime.now());
            }
        } else {
            ticket.setOpenedAt(OffsetDateTime.now());
        }

        ticket.setStatus(TicketStatus.open);
        ticket.setCreatedAt(OffsetDateTime.now());
        ticket.setUpdatedAt(OffsetDateTime.now());

        ticket = ticketRepository.save(ticket);

        if (attachment != null && !attachment.isEmpty()) {
            saveAttachment(ticket, attachment, requester, AttachmentSource.user_upload);
        }

        try {
            nlpService.analyze(ticket);
        } catch (Exception e) {
            log.warn("NLP analysis failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }

        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAllTickets() {
        return ticketRepository.findAll().stream()
                .map(TicketResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<TicketResponse> getTicket(UUID id) {
        return ticketRepository.findById(id).map(TicketResponse::from);
    }

    public BulkImportResponse bulkImport(MultipartFile file, User uploader) {
        var fileName = file.getOriginalFilename();
        var ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }

        var bulkImport = new BulkImport();
        bulkImport.setUploadedBy(uploader);
        bulkImport.setFileName(fileName != null ? fileName : "unknown");
        bulkImport.setFileFormat(ext);
        bulkImport.setTotalRows(0);
        bulkImport.setProcessedRows(0);
        bulkImport.setFailedRows(0);
        bulkImport.setStatus(ImportStatus.processing);
        bulkImport.setCreatedAt(OffsetDateTime.now());
        bulkImport = bulkImportRepository.save(bulkImport);

        try {
            List<CreateTicketRequest> requests;
            if ("csv".equals(ext)) {
                requests = parseCsv(file);
            } else if ("json".equals(ext)) {
                requests = parseJson(file);
            } else {
                throw new IllegalArgumentException("Unsupported file format: " + ext);
            }

            bulkImport.setTotalRows(requests.size());
            int processed = 0;
            int failed = 0;

            for (var req : requests) {
                try {
                    createTicket(req, uploader);
                    processed++;
                } catch (Exception e) {
                    log.warn("Failed to import ticket row: {}", e.getMessage());
                    failed++;
                }
            }

            bulkImport.setProcessedRows(processed);
            bulkImport.setFailedRows(failed);
            bulkImport.setStatus(failed > 0 ? ImportStatus.failed : ImportStatus.completed);
            bulkImport.setCompletedAt(OffsetDateTime.now());
            bulkImport = bulkImportRepository.save(bulkImport);

        } catch (Exception e) {
            bulkImport.setStatus(ImportStatus.failed);
            bulkImport.setCompletedAt(OffsetDateTime.now());
            bulkImport = bulkImportRepository.save(bulkImport);
            log.error("Bulk import failed", e);
        }

        return new BulkImportResponse(
                bulkImport.getId(),
                bulkImport.getFileName(),
                bulkImport.getFileFormat(),
                bulkImport.getTotalRows(),
                bulkImport.getProcessedRows(),
                bulkImport.getFailedRows(),
                bulkImport.getStatus()
        );
    }

    private Attachment saveAttachment(Ticket ticket, MultipartFile file, User uploader, AttachmentSource source) {
        try {
            var storedName = fileStorageService.store(file);
            var attachment = new Attachment();
            attachment.setTicket(ticket);
            attachment.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
            attachment.setFileSizeBytes(file.getSize());
            attachment.setMimeType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
            attachment.setStoragePath(storedName);
            attachment.setSource(source);
            attachment.setUploadedBy(uploader);
            attachment.setUploadedAt(OffsetDateTime.now());
            return attachmentRepository.save(attachment);
        } catch (Exception e) {
            log.warn("Failed to save attachment for ticket {}: {}", ticket.getId(), e.getMessage());
            return null;
        }
    }

    private List<CreateTicketRequest> parseCsv(MultipartFile file) throws Exception {
        var requests = new ArrayList<CreateTicketRequest>();
        try (var reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            var headerLine = reader.readLine();
            if (headerLine == null) return requests;

            var headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                var values = line.split(",");
                var map = new java.util.HashMap<String, String>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    map.put(headers[i].trim(), values[i].trim());
                }
                requests.add(new CreateTicketRequest(
                        map.getOrDefault("title", ""),
                        map.getOrDefault("description", ""),
                        map.get("categorySlug"),
                        map.get("urgencyReported"),
                        map.get("openedAt")
                ));
            }
        }
        return requests;
    }

    private List<CreateTicketRequest> parseJson(MultipartFile file) throws Exception {
        var bytes = file.getBytes();
        return objectMapper.readValue(bytes, new TypeReference<List<CreateTicketRequest>>() {});
    }
}
