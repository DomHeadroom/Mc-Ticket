package it.domheadroom.mc_ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "tickets", schema = "helpdesk")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Size(max = 500)
    @NotNull
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @NotNull
    @Column(name = "description", nullable = false, length = Integer.MAX_VALUE)
    private String description;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'open'")
    @Column(name = "status", columnDefinition = "ticket_status not null")
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'medium'")
    @Column(name = "urgency_reported", columnDefinition = "urgency_level not null")
    private UrgencyLevel urgencyReported;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_computed", columnDefinition = "priority_level")
    private PriorityLevel priorityComputed;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "category_id_user")
    private Category categoryIdUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "category_id_auto")
    private Category categoryIdAuto;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "assigned_agent_id")
    private User assignedAgent;

    @Size(max = 50)
    @NotNull
    @ColumnDefault("'manual'")
    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "bulk_import_id")
    private BulkImport bulkImport;

    @NotNull
    @ColumnDefault("false")
    @Column(name = "nlp_processed", nullable = false)
    private Boolean nlpProcessed;

    @Column(name = "nlp_processed_at")
    private OffsetDateTime nlpProcessedAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "opened_at", nullable = false)
    private OffsetDateTime openedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}