package it.domheadroom.mc_ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "ticket_nlp_analysis", schema = "helpdesk")
public class TicketNlpAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Size(max = 100)
    @NotNull
    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "suggested_category_id")
    private Category suggestedCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_priority", length = 50)
    private PriorityLevel suggestedPriority;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Size(max = 10)
    @Column(name = "language_detected", length = 10)
    private String languageDetected;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;


}