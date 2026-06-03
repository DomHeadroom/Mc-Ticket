package it.domheadroom.mc_ticket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record NlpAnalysisResponse(
    List<String> keywords,
    @JsonProperty("category_slug") String categorySlug,
    String priority,
    BigDecimal confidence
) {}