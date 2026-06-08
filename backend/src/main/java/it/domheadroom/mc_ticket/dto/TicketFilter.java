package it.domheadroom.mc_ticket.dto;

import java.time.LocalDate;

public record TicketFilter(
    String search,
    String status,
    String categorySlug,
    String priority,
    LocalDate dateFrom,
    LocalDate dateTo
) {}
