package it.domheadroom.mc_ticket.service;

import it.domheadroom.mc_ticket.dto.NlpAnalysisResponse;
import it.domheadroom.mc_ticket.dto.NlpRequest;
import it.domheadroom.mc_ticket.entity.*;
import it.domheadroom.mc_ticket.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class NlpService {

    private static final Logger log = LoggerFactory.getLogger(NlpService.class);

    private final RestClient restClient;
    private final CategoryRepository categoryRepository;
    private final KeywordRepository keywordRepository;
    private final TicketKeywordRepository ticketKeywordRepository;
    private final TicketNlpAnalysisRepository ticketNlpAnalysisRepository;

    public NlpService(
            @Value("${app.nlp.base-url}") String nlpBaseUrl,
            CategoryRepository categoryRepository,
            KeywordRepository keywordRepository,
            TicketKeywordRepository ticketKeywordRepository,
            TicketNlpAnalysisRepository ticketNlpAnalysisRepository
    ) {
        this.restClient = RestClient.create(nlpBaseUrl);
        this.categoryRepository = categoryRepository;
        this.keywordRepository = keywordRepository;
        this.ticketKeywordRepository = ticketKeywordRepository;
        this.ticketNlpAnalysisRepository = ticketNlpAnalysisRepository;
    }

    @Transactional
    public void analyze(Ticket ticket) {
        try {
            var response = callNlp(ticket.getTitle(), ticket.getDescription());

            var category = resolveCategory(response.categorySlug());
            var priority = resolvePriority(response.priority());

            persistAnalysis(ticket, response, category, priority);
            persistKeywords(ticket, response.keywords());

            ticket.setNlpProcessed(true);
            ticket.setNlpProcessedAt(OffsetDateTime.now());
            ticket.setCategoryIdAuto(category);
            ticket.setPriorityComputed(priority);
        } catch (Exception e) {
            log.warn("NLP analysis failed for ticket {}: {}", ticket.getId(), e.getMessage());
        }
    }

    private NlpAnalysisResponse callNlp(String title, String description) {
        var request = new NlpRequest(title, description);
        return restClient.post()
                .uri("/analyze")
                .body(request)
                .retrieve()
                .body(NlpAnalysisResponse.class);
    }

    private Category resolveCategory(String slug) {
        if (slug == null || slug.isBlank()) return null;
        return categoryRepository.findBySlug(slug).orElseGet(() -> {
            log.warn("Category with slug '{}' not found; skipping category assignment", slug);
            return null;
        });
    }

    private PriorityLevel resolvePriority(String priority) {
        if (priority == null || priority.isBlank()) return null;
        try {
            return PriorityLevel.valueOf(priority);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown priority '{}'; skipping priority assignment", priority);
            return null;
        }
    }

    private void persistAnalysis(Ticket ticket, NlpAnalysisResponse response, Category category, PriorityLevel priority) {
        var analysis = new TicketNlpAnalysis();
        analysis.setTicket(ticket);
        analysis.setModelVersion("nlp-service-1.0");
        analysis.setRawOutput(null);
        analysis.setSuggestedCategory(category);
        analysis.setSuggestedPriority(priority);
        analysis.setConfidenceScore(response.confidence());
        analysis.setLanguageDetected("it");
        analysis.setProcessedAt(OffsetDateTime.now());
        ticketNlpAnalysisRepository.save(analysis);
    }

    private void persistKeywords(Ticket ticket, List<String> terms) {
        ticketKeywordRepository.deleteByTicketId(ticket.getId());

        for (String term : terms) {
            var keyword = keywordRepository.findByTerm(term).orElseGet(() -> {
                var k = new Keyword();
                k.setTerm(term);
                k.setFrequency(0);
                return keywordRepository.save(k);
            });

            keyword.setFrequency(keyword.getFrequency() + 1);
            keywordRepository.save(keyword);

            var tk = new TicketKeyword();
            var id = new TicketKeywordId();
            id.setTicketId(ticket.getId());
            id.setKeywordId(keyword.getId());
            tk.setId(id);
            tk.setTicket(ticket);
            tk.setKeyword(keyword);
            tk.setExtractedAt(OffsetDateTime.now());
            ticketKeywordRepository.save(tk);
        }
    }

}
