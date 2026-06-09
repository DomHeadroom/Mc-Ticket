package it.domheadroom.mc_ticket.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.domheadroom.mc_ticket.dto.BulkImportResponse;
import it.domheadroom.mc_ticket.dto.CategoryResponse;
import it.domheadroom.mc_ticket.dto.CreateTicketRequest;
import it.domheadroom.mc_ticket.dto.TicketFilter;
import it.domheadroom.mc_ticket.dto.TicketResponse;
import it.domheadroom.mc_ticket.entity.*;
import it.domheadroom.mc_ticket.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final CategoryRepository categoryRepository;
    private final AttachmentRepository attachmentRepository;
    private final BulkImportRepository bulkImportRepository;
    private final FileStorageService fileStorageService;
    private final NlpService nlpService;
    private final ObjectMapper objectMapper;
    private final TicketKeywordRepository ticketKeywordRepository;
    private final TransactionTemplate transactionTemplate;

    public TicketService(TicketRepository ticketRepository,
                         CategoryRepository categoryRepository,
                         AttachmentRepository attachmentRepository,
                         BulkImportRepository bulkImportRepository,
                         FileStorageService fileStorageService,
                         NlpService nlpService,
                         ObjectMapper objectMapper,
                         TicketKeywordRepository ticketKeywordRepository,
                         TransactionTemplate transactionTemplate) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.attachmentRepository = attachmentRepository;
        this.bulkImportRepository = bulkImportRepository;
        this.fileStorageService = fileStorageService;
        this.nlpService = nlpService;
        this.objectMapper = objectMapper;
        this.ticketKeywordRepository = ticketKeywordRepository;
        this.transactionTemplate = transactionTemplate;
    }

    public TicketResponse createTicket(CreateTicketRequest req, User requester) {
        return createTicket(req, requester, null, "manual");
    }

    public TicketResponse createTicket(CreateTicketRequest req, User requester, MultipartFile attachment) {
        return createTicket(req, requester, attachment, "manual");
    }

    public TicketResponse createTicket(CreateTicketRequest req, User requester, MultipartFile attachment, String source) {
        var ticket = transactionTemplate.execute(status -> persistTicket(req, requester, attachment, source));

        try {
            nlpService.analyze(ticket);
            transactionTemplate.execute(status -> ticketRepository.save(ticket));
        } catch (Exception e) {
            log.warn("NLP analysis failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }

        return TicketResponse.from(ticket, List.of(), 0);
    }

    private Ticket persistTicket(CreateTicketRequest req, User requester, MultipartFile attachment, String source) {
        var ticket = new Ticket();
        ticket.setTitle(req.title());
        ticket.setDescription(req.description());
        ticket.setRequester(requester);
        ticket.setSource(source);
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

        return ticket;
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> getAllTickets(Pageable pageable) {
        var page = ticketRepository.findAll(pageable);
        var tickets = page.getContent();
        var ids = tickets.stream().map(Ticket::getId).toList();

        var keywordsById = ticketKeywordRepository.findByIdTicketIdIn(ids).stream()
            .collect(Collectors.groupingBy(
                tk -> tk.getId().getTicketId(),
                Collectors.mapping(tk -> tk.getKeyword().getTerm(), Collectors.toList())
            ));

        var attachmentCounts = attachmentRepository.findByTicketIdIn(ids).stream()
            .collect(Collectors.groupingBy(
                a -> a.getTicket().getId(),
                Collectors.counting()
            ));

        return page.map(t -> TicketResponse.from(t,
            keywordsById.getOrDefault(t.getId(), List.of()),
            attachmentCounts.getOrDefault(t.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public Page<TicketResponse> searchTickets(TicketFilter filter, Pageable pageable) {
        var spec = TicketSpecifications.fromFilter(filter);
        var page = ticketRepository.findAll(spec, pageable);
        var tickets = page.getContent();
        var ids = tickets.stream().map(Ticket::getId).toList();

        var keywordsById = ticketKeywordRepository.findByIdTicketIdIn(ids).stream()
            .collect(Collectors.groupingBy(
                tk -> tk.getId().getTicketId(),
                Collectors.mapping(tk -> tk.getKeyword().getTerm(), Collectors.toList())
            ));

        var attachmentCounts = attachmentRepository.findByTicketIdIn(ids).stream()
            .collect(Collectors.groupingBy(
                a -> a.getTicket().getId(),
                Collectors.counting()
            ));

        return page.map(t -> TicketResponse.from(t,
            keywordsById.getOrDefault(t.getId(), List.of()),
            attachmentCounts.getOrDefault(t.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public Optional<TicketResponse> getTicket(UUID id) {
        return ticketRepository.findById(id)
                .map(t -> TicketResponse.from(t,
                    ticketKeywordRepository.findByIdTicketId(id).stream()
                        .map(tk -> tk.getKeyword().getTerm())
                        .toList(),
                    attachmentRepository.countByTicketId(id)));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getActiveCategories() {
        return categoryRepository.findByIsActiveTrue().stream()
                .map(CategoryResponse::from)
                .toList();
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

            var source = "bulk_" + ext;
            for (var req : requests) {
                try {
                    createTicket(req, uploader, null, source);
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
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             var parser = CSVParser.parse(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            for (var record : parser) {
                requests.add(new CreateTicketRequest(
                        record.get("title"),
                        record.get("description"),
                        record.get("categorySlug"),
                        record.get("urgencyReported"),
                        record.get("openedAt")
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
