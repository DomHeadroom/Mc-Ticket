package it.domheadroom.mc_ticket.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "bulk_imports", schema = "helpdesk")
public class BulkImport {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Size(max = 500)
    @NotNull
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Size(max = 10)
    @NotNull
    @Column(name = "file_format", nullable = false, length = 10)
    private String fileFormat;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "processed_rows", nullable = false)
    private Integer processedRows;

    @NotNull
    @ColumnDefault("0")
    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'queued'")
    @Column(name = "status", columnDefinition = "import_status not null")
    private ImportStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_log")
    private Map<String, Object> errorLog;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;


}