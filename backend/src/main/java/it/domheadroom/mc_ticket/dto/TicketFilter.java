package it.domheadroom.mc_ticket.dto;

import java.time.LocalDate;
import java.util.UUID;

public record TicketFilter(
    String search,
    String status,
    String categorySlug,
    String priority,
    LocalDate dateFrom,
    LocalDate dateTo,
    UUID requesterId 
) {}

