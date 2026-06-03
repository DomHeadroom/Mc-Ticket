package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
}