package it.domheadroom.mc_ticket.controller;

import it.domheadroom.mc_ticket.dto.BulkImportResponse;
import it.domheadroom.mc_ticket.dto.CategoryResponse;
import it.domheadroom.mc_ticket.dto.CreateTicketRequest;
import it.domheadroom.mc_ticket.dto.TicketFilter;
import it.domheadroom.mc_ticket.dto.TicketResponse;
import it.domheadroom.mc_ticket.entity.User;
import it.domheadroom.mc_ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping(value = "/tickets", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TicketResponse> createTicketJson(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal User user) {
        var response = ticketService.createTicket(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketResponse> createTicketWithAttachment(
            @RequestPart("ticket") @Valid CreateTicketRequest request,
            @RequestPart(value = "attachment", required = false) MultipartFile attachment,
            @AuthenticationPrincipal User user) {
        var response = ticketService.createTicket(request, user, attachment);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/tickets/bulk")
    public ResponseEntity<BulkImportResponse> bulkImport(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var response = ticketService.bulkImport(file, user);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tickets")
    public ResponseEntity<Page<TicketResponse>> getAllTickets(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categorySlug,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal User user) {

        boolean isAdmin = user.getRole().equalsIgnoreCase("admin");
        UUID requesterId = isAdmin ? null : user.getId();

        var filter = new TicketFilter(search, status, categorySlug, priority, dateFrom, dateTo, requesterId);
        var hasFilters = search != null || status != null || categorySlug != null
            || priority != null || dateFrom != null || dateTo != null;

        if (hasFilters || requesterId != null) {
            return ResponseEntity.ok(ticketService.searchTickets(filter, pageable));
        }
        return ResponseEntity.ok(ticketService.getAllTickets(pageable));
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
        return ticketService.getTicket(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(ticketService.getActiveCategories());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}

