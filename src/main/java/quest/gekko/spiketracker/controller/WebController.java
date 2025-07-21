package quest.gekko.spiketracker.controller;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import quest.gekko.spiketracker.model.match.MatchHistory;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.MatchHistoryService;
import quest.gekko.spiketracker.service.MatchTrackingService;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@Validated
public class WebController {
    private final MatchTrackingService matchTrackingService;
    private final MatchHistoryService matchHistoryService;
    private final MeterRegistry meterRegistry;

    @Value("${app.api.max-history-limit:100}")
    private int maxHistoryLimit;

    public WebController(final MatchTrackingService matchTrackingService,
                         final MatchHistoryService matchHistoryService,
                         final MeterRegistry meterRegistry) {
        this.matchTrackingService = matchTrackingService;
        this.matchHistoryService = matchHistoryService;
        this.meterRegistry = meterRegistry;
    }

    @GetMapping("/")
    @Timed(value = "page.load.time", description = "Time taken to load main page")
    public String index(final Model model) {
        try {
            model.addAttribute("matches", matchTrackingService.getLiveMatches());
            model.addAttribute("recentMatches", matchHistoryService.getRecentCompletedMatches(5));
            model.addAttribute("lastUpdate", LocalDateTime.now());
            model.addAttribute("isHealthy", matchTrackingService.isHealthy());
            return "index";
        } catch (Exception e) {
            log.error("Error loading main page: {}", e.getMessage(), e);
            model.addAttribute("error", "Unable to load match data");
            return "error";
        }
    }

    @GetMapping("/api/matches")
    @ResponseBody
    @Timed(value = "api.matches.time", description = "Time taken to fetch live matches")
    public ResponseEntity<ApiResponse<Collection<MatchSegment>>> matches() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "matches").increment();

            final Collection<MatchSegment> matches = matchTrackingService.getLiveMatches().values();

            return ResponseEntity.ok(ApiResponse.success(matches, "Retrieved " + matches.size() + " live matches"));
        } catch (Exception e) {
            log.error("Error fetching live matches: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "matches").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to retrieve live matches"));
        }
    }

    @GetMapping("/api/matches/history")
    @ResponseBody
    @Timed(value = "api.history.time", description = "Time taken to fetch match history")
    public ResponseEntity<ApiResponse<List<MatchHistory>>> matchHistory(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit cannot exceed 100")
            final int limit) {
        try {
            meterRegistry.counter("api.requests", "endpoint", "history").increment();

            final int effectiveLimit = Math.min(limit, maxHistoryLimit);

            final List<MatchHistory> history = matchHistoryService.getRecentCompletedMatches(effectiveLimit);

            return ResponseEntity.ok(ApiResponse.success(history, "Retrieved " + history.size() + " match history records"));
        } catch (final Exception e) {
            log.error("Error fetching match history: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "history").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve match history"));
        }
    }

    @GetMapping("/api/matches/team/{teamName}")
    @ResponseBody
    @Timed(value = "api.team.matches.time", description = "Time taken to fetch team matches")
    public ResponseEntity<ApiResponse<List<MatchHistory>>> teamMatches(
            @PathVariable String teamName,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 50, message = "Limit cannot exceed 50")
            final int limit) {
        try {
            meterRegistry.counter("api.requests", "endpoint", "team-matches").increment();

            if (teamName == null || teamName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Team name is required"));
            }

            final String sanitizedTeamName = teamName.trim().replaceAll("[^a-zA-Z0-9\\s-_]", "");
            if (sanitizedTeamName.length() > 50) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Team name too long"));
            }

            final List<MatchHistory> matches = matchHistoryService.getMatchesForTeam(sanitizedTeamName, limit);

            return ResponseEntity.ok(ApiResponse.success(matches, "Retrieved " + matches.size() + " matches for team " + sanitizedTeamName));

        } catch (final Exception e) {
            log.error("Error fetching team matches for {}: {}", teamName, e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "team-matches").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to retrieve team matches"));
        }
    }

    @GetMapping("/api/health")
    @ResponseBody
    @Cacheable(value = "healthStatus", key = "'health'")
    public ResponseEntity<HealthStatus> health() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "health").increment();

            final Map<String, Object> healthDetails = matchTrackingService.getHealthDetails();
            final boolean isHealthy = matchTrackingService.isHealthy();

            final HealthStatus status = new HealthStatus(
                    isHealthy ? "UP" : "DOWN",
                    matchTrackingService.getLastUpdateTime(),
                    matchTrackingService.getLiveMatchCount(),
                    LocalDateTime.now(),
                    healthDetails
            );

            return ResponseEntity.ok(status);
        } catch (final Exception e) {
            log.error("Error checking health: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "health").increment();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new HealthStatus("DOWN", 0L, 0, LocalDateTime.now(),
                            Map.of("error", e.getMessage())));
        }
    }

    @GetMapping("/api/stats")
    @ResponseBody
    @Cacheable(value = "apiStats", key = "'stats'")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "stats").increment();

            final Map<String, Long> teamStats = matchHistoryService.getTeamStats();
            final Double avgDuration = matchHistoryService.getAverageMatchDuration();
            final List<Object[]> eventStats = matchHistoryService.getEventStatistics();

            final Map<String, Object> stats = Map.of(
                    "teamStats", teamStats,
                    "averageMatchDuration", avgDuration != null ? avgDuration : 0.0,
                    "eventStatistics", eventStats,
                    "totalMatches", teamStats.values().stream().mapToLong(Long::longValue).sum() / 2,
                    "generatedAt", LocalDateTime.now()
            );

            return ResponseEntity.ok(ApiResponse.success(stats, "Statistics retrieved successfully"));
        } catch (final Exception e) {
            log.error("Error fetching statistics: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "stats").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to retrieve statistics"));
        }
    }

    @MessageMapping("/matches/subscribe")
    @SendTo("/topic/matches")
    @Timed(value = "websocket.subscribe.time", description = "Time taken for WebSocket subscription")
    public Collection<MatchSegment> subscribeToMatches() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "websocket-subscribe").increment();
            return matchTrackingService.getLiveMatches().values();
        } catch (final Exception e) {
            log.error("Error handling WebSocket subscription: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "websocket-subscribe").increment();
            return List.of();
        }
    }

    @PostMapping("/api/admin/refresh")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> forceRefresh() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "admin-refresh").increment();
            matchTrackingService.forceRefresh();
            return ResponseEntity.ok(ApiResponse.success("OK", "Match data refreshed successfully"));
        } catch (final Exception e) {
            log.error("Error during forced refresh: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "admin-refresh").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to refresh match data"));
        }
    }

    @PostMapping("/api/admin/clear-cache")
    @ResponseBody
    public ResponseEntity<ApiResponse<String>> clearCache() {
        try {
            meterRegistry.counter("api.requests", "endpoint", "admin-clear-cache").increment();
            matchTrackingService.clearCache();
            return ResponseEntity.ok(ApiResponse.success("OK", "Cache cleared successfully"));
        } catch (final Exception e) {
            log.error("Error clearing cache: {}", e.getMessage(), e);
            meterRegistry.counter("api.errors", "endpoint", "admin-clear-cache").increment();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Failed to clear cache"));
        }
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationError(final ConstraintViolationException e) {
        log.warn("Validation error: {}", e.getMessage());
        meterRegistry.counter("api.errors", "type", "validation").increment();
        return ResponseEntity.badRequest().body(ApiResponse.error("Invalid parameters: " + e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericError(final Exception e) {
        log.error("Unexpected error in controller: {}", e.getMessage(), e);
        meterRegistry.counter("api.errors", "type", "generic").increment();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("An unexpected error occurred"));
    }

    public record HealthStatus(
            String status,
            long lastUpdate,
            int liveMatches,
            LocalDateTime timestamp,
            Map<String, Object> details
    ) {}

    @Getter
    public static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String message;
        private final LocalDateTime timestamp;

        private ApiResponse(final boolean success, final T data, final String message) {
            this.success = success;
            this.data = data;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }

        public static <T> ApiResponse<T> success(final T data, final String message) {
            return new ApiResponse<>(true, data, message);
        }

        public static <T> ApiResponse<T> error(final String message) {
            return new ApiResponse<>(false, null, message);
        }

    }
}