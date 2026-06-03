package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.TicketKeyword;
import it.domheadroom.mc_ticket.entity.TicketKeywordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketKeywordRepository extends JpaRepository<TicketKeyword, TicketKeywordId> {
    void deleteByTicketId(UUID ticketId);
}