package it.domheadroom.mc_ticket.dto;

import it.domheadroom.mc_ticket.entity.Ticket;
import it.domheadroom.mc_ticket.entity.TicketStatus;
import it.domheadroom.mc_ticket.entity.UrgencyLevel;
import it.domheadroom.mc_ticket.entity.PriorityLevel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TicketResponse(
    UUID id,
    String title,
    String description,
    TicketStatus status,
    UrgencyLevel urgencyReported,
    PriorityLevel priorityComputed,
    String categoryUser,
    String categoryAuto,
    String requesterName,
    String requesterEmail,
    String assignedAgentName,
    String source,
    boolean nlpProcessed,
    List<String> keywords,
    OffsetDateTime openedAt,
    OffsetDateTime resolvedAt,
    OffsetDateTime closedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static TicketResponse from(Ticket t, List<String> keywords) {
        return new TicketResponse(
            t.getId(),
            t.getTitle(),
            t.getDescription(),
            t.getStatus(),
            t.getUrgencyReported(),
            t.getPriorityComputed(),
            t.getCategoryIdUser() != null ? t.getCategoryIdUser().getName() : null,
            t.getCategoryIdAuto() != null ? t.getCategoryIdAuto().getName() : null,
            t.getRequester().getFullName(),
            t.getRequester().getEmail(),
            t.getAssignedAgent() != null ? t.getAssignedAgent().getFullName() : null,
            t.getSource(),
            t.getNlpProcessed(),
            keywords,
            t.getOpenedAt(),
            t.getResolvedAt(),
            t.getClosedAt(),
            t.getCreatedAt(),
            t.getUpdatedAt()
        );
    }
}
