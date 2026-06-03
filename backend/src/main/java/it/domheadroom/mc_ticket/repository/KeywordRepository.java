package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KeywordRepository extends JpaRepository<Keyword, Integer> {
    Optional<Keyword> findByTerm(String term);
}