package it.domheadroom.mc_ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketRequest(
    @NotBlank @Size(max = 500) String title,
    @NotBlank String description,
    String categorySlug,
    String urgencyReported,
    String openedAt
) {}
