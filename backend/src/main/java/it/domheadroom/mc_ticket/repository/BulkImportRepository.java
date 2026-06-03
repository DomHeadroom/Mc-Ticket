package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.BulkImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BulkImportRepository extends JpaRepository<BulkImport, UUID> {
}
