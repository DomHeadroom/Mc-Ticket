package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.TicketNlpAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketNlpAnalysisRepository extends JpaRepository<TicketNlpAnalysis, UUID> {
    Optional<TicketNlpAnalysis> findByTicketId(UUID ticketId);
}