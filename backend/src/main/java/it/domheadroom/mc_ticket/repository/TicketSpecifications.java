package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.dto.TicketFilter;
import it.domheadroom.mc_ticket.entity.PriorityLevel;
import it.domheadroom.mc_ticket.entity.Ticket;
import it.domheadroom.mc_ticket.entity.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;

public class TicketSpecifications {

    public static Specification<Ticket> fromFilter(TicketFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (filter.search() != null && !filter.search().isBlank()) {
                var pattern = "%" + filter.search().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
                ));
            }

            if (filter.status() != null && !filter.status().isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("status"), TicketStatus.valueOf(filter.status())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (filter.categorySlug() != null && !filter.categorySlug().isBlank()) {
                predicates.add(cb.equal(root.get("categoryIdAuto").get("slug"), filter.categorySlug()));
            }

            if (filter.priority() != null && !filter.priority().isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("priorityComputed"), PriorityLevel.valueOf(filter.priority())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"), filter.dateFrom().atStartOfDay().atOffset(ZoneOffset.UTC)));
            }

            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("createdAt"), filter.dateTo().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
