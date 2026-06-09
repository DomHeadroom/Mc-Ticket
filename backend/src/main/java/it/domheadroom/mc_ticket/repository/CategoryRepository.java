package it.domheadroom.mc_ticket.repository;

import it.domheadroom.mc_ticket.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {
    Optional<Category> findBySlug(String slug);

    List<Category> findByIsActiveTrue();
}