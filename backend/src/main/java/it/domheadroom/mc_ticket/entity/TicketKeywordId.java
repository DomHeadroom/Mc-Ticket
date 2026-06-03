package it.domheadroom.mc_ticket.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class TicketKeywordId implements Serializable {
    private static final long serialVersionUID = -3652624546766927088L;
    @NotNull
    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @NotNull
    @Column(name = "keyword_id", nullable = false)
    private Integer keywordId;


}