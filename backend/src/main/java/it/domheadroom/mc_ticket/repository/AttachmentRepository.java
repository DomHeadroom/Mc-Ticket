package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    int countByTicketId(UUID ticketId);

    List<Attachment> findByTicketIdIn(Collection<UUID> ticketIds);
}
