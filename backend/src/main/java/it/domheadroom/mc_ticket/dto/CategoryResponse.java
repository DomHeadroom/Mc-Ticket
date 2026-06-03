package it.domheadroom.mc_ticket.dto;

import it.domheadroom.mc_ticket.entity.Category;

public record CategoryResponse(
    Integer id,
    String name,
    String slug,
    String description
) {
    public static CategoryResponse from(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug(), c.getDescription());
    }
}
