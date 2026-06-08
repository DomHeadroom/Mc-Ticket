package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.TicketKeyword;
import it.domheadroom.mc_ticket.entity.TicketKeywordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TicketKeywordRepository extends JpaRepository<TicketKeyword, TicketKeywordId> {

    void deleteByTicketId(UUID ticketId);

    List<TicketKeyword> findByIdTicketIdOrderByRelevanceScoreDesc(UUID ticketId);

    List<TicketKeyword> findByIdTicketIdIn(Collection<UUID> ticketIds);
}
