package it.domheadroom.mc_ticket.controller;

import it.domheadroom.mc_ticket.dto.BulkImportResponse;
import it.domheadroom.mc_ticket.dto.CategoryResponse;
import it.domheadroom.mc_ticket.dto.CreateTicketRequest;
import it.domheadroom.mc_ticket.dto.TicketResponse;
import it.domheadroom.mc_ticket.entity.User;
import it.domheadroom.mc_ticket.repository.CategoryRepository;
import it.domheadroom.mc_ticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;
    private final CategoryRepository categoryRepository;

    public TicketController(TicketService ticketService, CategoryRepository categoryRepository) {
        this.ticketService = ticketService;
        this.categoryRepository = categoryRepository;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/tickets")
    public ResponseEntity<List<TicketResponse>> getAllTickets() {
        return ResponseEntity.ok(ticketService.getAllTickets());
    }

    @GetMapping("/tickets/{id}")
    public ResponseEntity<TicketResponse> getTicket(@PathVariable UUID id) {
        return ticketService.getTicket(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getActiveCategories() {
        var categories = categoryRepository.findAll().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .map(CategoryResponse::from)
                .toList();
        return ResponseEntity.ok(categories);
    }
}
