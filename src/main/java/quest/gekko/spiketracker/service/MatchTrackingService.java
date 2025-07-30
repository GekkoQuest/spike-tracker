package quest.gekko.spiketracker.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.api.VlrggMatchApiClient;
import quest.gekko.spiketracker.util.StreamLinkScraper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchTrackingService {
    private final VlrggMatchApiClient apiClient;
    private final MatchHistoryService historyService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamLinkScraper streamLinkScraper;
    private final MeterRegistry meterRegistry;
    private final AdaptivePollingService adaptivePolling;
    private final TaskScheduler taskScheduler;

    private final int maxConsecutiveFailures;
    private final long healthCheckThresholdMs;
    private final boolean enableStreamScraping;

    private final Timer updateCycleTimer;

    private final Map<String, MatchSegment> liveMatches = new ConcurrentHashMap<>();
    private final Map<String, String> streamLinkCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastUpdateTimes = new ConcurrentHashMap<>();

    @Getter
    private long lastUpdateTime = System.currentTimeMillis();

    private boolean isHealthy = true;
    private int consecutiveFailures = 0;
    private LocalDateTime lastSuccessfulUpdate = LocalDateTime.now();

    private ScheduledFuture<?> scheduledTask;
    private volatile boolean isShuttingDown = false;

    public MatchTrackingService(
            final VlrggMatchApiClient apiClient,
            final MatchHistoryService historyService,
            final SimpMessagingTemplate messagingTemplate,
            final StreamLinkScraper streamLinkScraper,
            final MeterRegistry meterRegistry,
            final AdaptivePollingService adaptivePolling,
            final TaskScheduler taskScheduler,
            @Value("${app.match-tracking.max-consecutive-failures:5}") final int maxConsecutiveFailures,
            @Value("${app.match-tracking.health-check-threshold-ms:60000}") final long healthCheckThresholdMs,
            @Value("${app.match-tracking.enable-stream-scraping:true}") final boolean enableStreamScraping) {

        this.apiClient = apiClient;
        this.historyService = historyService;
        this.messagingTemplate = messagingTemplate;
        this.streamLinkScraper = streamLinkScraper;
        this.meterRegistry = meterRegistry;
        this.adaptivePolling = adaptivePolling;
        this.taskScheduler = taskScheduler;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.healthCheckThresholdMs = healthCheckThresholdMs;
        this.enableStreamScraping = enableStreamScraping;

        this.updateCycleTimer = Timer.builder("match.update.cycle")
                .description("Time taken for match update cycle")
                .register(meterRegistry);
    }

    @PostConstruct
    public void startAdaptivePolling() {
        log.info("Starting adaptive polling for match tracking");
        scheduleNextUpdate();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down match tracking service");
        isShuttingDown = true;

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
            log.info("Cancelled scheduled polling task");
        }
    }

    private void scheduleNextUpdate() {
        if (isShuttingDown) {
            return;
        }

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
        }

        final boolean hasMatches = !liveMatches.isEmpty();
        final int interval = adaptivePolling.getNextInterval(hasMatches);

        scheduledTask = taskScheduler.schedule(
                this::updateMatchesAndReschedule,
                Instant.now().plusMillis(interval)
        );

        if (hasMatches) {
            log.debug("Active polling - {} live matches, next update in {} seconds", liveMatches.size(), interval / 1000);
        } else {
            log.debug("Idle polling mode: {}, next update in {} seconds", adaptivePolling.getPollingMode(), interval / 1000);
        }

        meterRegistry.gauge("match.polling.interval", interval);
        meterRegistry.gauge("match.polling.hourly_operations", adaptivePolling.calculateHourlyDbConnections());
    }

    private void updateMatchesAndReschedule() {
        try {
            updateMatches();
        } finally {
            scheduleNextUpdate();
        }
    }

    public void updateMatches() {
        final Timer.Sample sample = Timer.start();

        try {
            final List<MatchSegment> currentMatches = fetchCurrentMatches();

            if (currentMatches == null) {
                handleApiFailure();
                return;
            }

            processMatchUpdates(currentMatches);
            broadcastUpdates();

            consecutiveFailures = 0;
            lastSuccessfulUpdate = LocalDateTime.now();
            lastUpdateTime = System.currentTimeMillis();
            isHealthy = true;

            meterRegistry.counter("match.updates", "status", "success").increment();

            // final String currentMode = adaptivePolling.getPollingMode();
            if (currentMatches.isEmpty() && adaptivePolling.getConsecutiveEmptyPolls() == 1) {
                log.info("No live matches found, switching to idle polling");
            } else if (!currentMatches.isEmpty() && adaptivePolling.getConsecutiveEmptyPolls() > 0) {
                log.info("Live matches resumed ({} matches), switching to active polling", currentMatches.size());
            }

        } catch (final Exception e) {
            handleUpdateException(e);
        } finally {
            sample.stop(updateCycleTimer);
        }
    }

    private List<MatchSegment> fetchCurrentMatches() {
        try {
            final LiveMatchData data = apiClient.getLiveMatchData();
            return data != null ? data.segments() : null;
        } catch (Exception e) {
            log.warn("Failed to fetch current matches: {}", e.getMessage());
            meterRegistry.counter("api.errors", "source", "vlrgg", "type", "fetch_failure").increment();
            return null;
        }
    }

    private void processMatchUpdates(final List<MatchSegment> currentMatches) {
        final Set<String> currentMatchIds = currentMatches.stream()
                .map(MatchSegment::match_page)
                .collect(Collectors.toSet());

        final Set<String> completedMatchIds = liveMatches.keySet().stream()
                .filter(matchId -> !currentMatchIds.contains(matchId))
                .collect(Collectors.toSet());

        completedMatchIds.forEach(this::handleCompletedMatch);
        currentMatches.forEach(this::handleMatchUpdate);

        cleanupStaleData();
    }

    private void handleMatchUpdate(final MatchSegment segment) {
        final String matchId = segment.match_page();

        if (!isValidMatchSegment(segment)) {
            log.warn("Invalid match segment received for {}", matchId);
            meterRegistry.counter("match.processing", "status", "invalid").increment();
            return;
        }

        final MatchSegment previousSegment = liveMatches.get(matchId);
        lastUpdateTimes.put(matchId, LocalDateTime.now());

        if (previousSegment == null) {
            handleNewMatch(segment, matchId);
        } else if (hasSignificantChanges(previousSegment, segment)) {
            handleMatchChanges(segment, matchId, previousSegment);
        }
    }

    private boolean isValidMatchSegment(final MatchSegment segment) {
        return segment != null &&
                segment.match_page() != null &&
                segment.team1() != null &&
                segment.team2() != null &&
                !segment.team1().trim().isEmpty() &&
                !segment.team2().trim().isEmpty();
    }

    private void handleNewMatch(final MatchSegment segment, final String matchId) {
        try {
            log.info("New match detected: {} vs {} ({})",
                    segment.team1(), segment.team2(), matchId);

            historyService.recordMatchStart(matchId, segment);

            if (enableStreamScraping) {
                scrapeStreamLinkAsync(segment, matchId);
            }

            liveMatches.put(matchId, segment);
            failureCount.remove(matchId);

            meterRegistry.counter("match.events", "type", "new").increment();

            if (liveMatches.size() == 1) {
                log.info("First live match detected, resetting to active polling");
                adaptivePolling.reset();
            }
        } catch (final Exception e) {
            log.error("Failed to handle new match {}: {}", matchId, e.getMessage(), e);
            meterRegistry.counter("match.processing", "status", "error", "type", "new_match").increment();
        }
    }

    private void handleMatchChanges(MatchSegment segment, final String matchId, final MatchSegment previousSegment) {
        try {
            if (hasScoreChanged(previousSegment, segment)) {
                log.info("Score updated in {}: {} vs {} ({}-{})",
                        matchId, segment.team1(), segment.team2(),
                        segment.score1(), segment.score2());

                historyService.updateMatchScore(matchId, segment.score1(), segment.score2(),
                        segment.current_map(), segment.streamLink());

                meterRegistry.counter("match.events", "type", "score_update").increment();
            }

            final String cachedStreamLink = streamLinkCache.get(matchId);

            if ((segment.streamLink() == null || segment.streamLink().isEmpty()) && cachedStreamLink != null) {
                segment = segment.withStreamLink(cachedStreamLink);
            }

            liveMatches.put(matchId, segment);
        } catch (final Exception e) {
            log.error("Failed to handle match changes for {}: {}", matchId, e.getMessage(), e);
            meterRegistry.counter("match.processing", "status", "error", "type", "update").increment();
        }
    }

    private void handleCompletedMatch(final String matchId) {
        try {
            final MatchSegment completedSegment = liveMatches.remove(matchId);

            if (completedSegment != null) {
                log.info("Match completed: {} vs {} (Final: {}-{})",
                        completedSegment.team1(), completedSegment.team2(),
                        completedSegment.score1(), completedSegment.score2());

                historyService.recordMatchCompletion(completedSegment);
                meterRegistry.counter("match.events", "type", "completed").increment();
            }

            streamLinkCache.remove(matchId);
            failureCount.remove(matchId);
            lastUpdateTimes.remove(matchId);
        } catch (final Exception e) {
            log.error("Failed to handle completed match {}: {}", matchId, e.getMessage(), e);
            meterRegistry.counter("match.processing", "status", "error", "type", "completion").increment();
        }
    }

    private void scrapeStreamLinkAsync(final MatchSegment segment, final String matchId) {
        CompletableFuture.supplyAsync(() -> {
                    try {
                        return streamLinkScraper.scrapeStreamLink(segment.match_page());
                    } catch (final Exception e) {
                        log.warn("Failed to scrape stream link for {}: {}", matchId, e.getMessage());
                        meterRegistry.counter("stream.scraping", "status", "failed").increment();
                        return null;
                    }
                }).thenAccept(streamLink -> {
                    if (streamLink != null && !streamLink.isEmpty()) {
                        streamLinkCache.put(matchId, streamLink);

                        final MatchSegment currentSegment = liveMatches.get(matchId);

                        if (currentSegment != null) {
                            MatchSegment updatedSegment = currentSegment.withStreamLink(streamLink);
                            liveMatches.put(matchId, updatedSegment);

                            try {
                                messagingTemplate.convertAndSend("/topic/matches", liveMatches.values());
                                meterRegistry.counter("stream.scraping", "status", "success").increment();
                            } catch (final Exception e) {
                                log.warn("Failed to broadcast stream link update: {}", e.getMessage());
                                meterRegistry.counter("websocket.broadcast", "status", "failed").increment();
                            }
                        }
                    }
                }).orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(ignored -> {
                    log.warn("Stream link scraping timed out or failed for {}", matchId);
                    meterRegistry.counter("stream.scraping", "status", "timeout").increment();
                    return null;
                });
    }

    private void broadcastUpdates() {
        try {
            messagingTemplate.convertAndSend("/topic/matches", liveMatches.values());
            meterRegistry.counter("websocket.broadcast", "status", "success").increment();
        } catch (Exception e) {
            log.error("Failed to broadcast match updates: {}", e.getMessage(), e);
            meterRegistry.counter("websocket.broadcast", "status", "failed").increment();
        }
    }

    private void handleApiFailure() {
        consecutiveFailures++;
        isHealthy = consecutiveFailures < maxConsecutiveFailures;

        log.warn("API failure #{} - Health status: {}", consecutiveFailures, isHealthy ? "DEGRADED" : "UNHEALTHY");

        meterRegistry.counter("match.updates", "status", "failed").increment();

        liveMatches.keySet().forEach(matchId ->
                failureCount.merge(matchId, 1, Integer::sum));
    }

    private void handleUpdateException(final Exception e) {
        consecutiveFailures++;
        isHealthy = false;

        log.error("Error during match update cycle #{}: {}", consecutiveFailures, e.getMessage(), e);
        meterRegistry.counter("match.updates", "status", "exception").increment();
    }

    private void cleanupStaleData() {
        final LocalDateTime cutoff = LocalDateTime.now().minusHours(1);

        lastUpdateTimes.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        final Set<String> staleMatches = failureCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 10)
                .map(Map.Entry::getKey)
                .filter(matchId -> {
                    LocalDateTime lastUpdate = lastUpdateTimes.get(matchId);
                    return lastUpdate == null || lastUpdate.isBefore(cutoff);
                })
                .collect(Collectors.toSet());

        staleMatches.forEach(matchId -> {
            log.warn("Removing stale match {}", matchId);
            liveMatches.remove(matchId);
            streamLinkCache.remove(matchId);
            failureCount.remove(matchId);
            meterRegistry.counter("match.cleanup", "type", "stale").increment();
        });
    }

    private boolean hasSignificantChanges(final MatchSegment oldSegment, final MatchSegment newSegment) {
        return hasScoreChanged(oldSegment, newSegment) ||
                !Objects.equals(oldSegment.current_map(), newSegment.current_map()) ||
                !Objects.equals(oldSegment.streamLink(), newSegment.streamLink()) ||
                !Objects.equals(oldSegment.time_until_match(), newSegment.time_until_match());
    }

    private boolean hasScoreChanged(final MatchSegment oldSegment, final MatchSegment newSegment) {
        return !Objects.equals(oldSegment.score1(), newSegment.score1()) ||
                !Objects.equals(oldSegment.score2(), newSegment.score2()) ||
                !Objects.equals(oldSegment.team1_round_ct(), newSegment.team1_round_ct()) ||
                !Objects.equals(oldSegment.team1_round_t(), newSegment.team1_round_t()) ||
                !Objects.equals(oldSegment.team2_round_ct(), newSegment.team2_round_ct()) ||
                !Objects.equals(oldSegment.team2_round_t(), newSegment.team2_round_t());
    }

    public Map<String, MatchSegment> getLiveMatches() {
        return Map.copyOf(liveMatches);
    }

    public boolean isHealthy() {
        final long timeSinceLastUpdate = System.currentTimeMillis() - lastUpdateTime;
        return isHealthy && timeSinceLastUpdate < healthCheckThresholdMs;
    }

    public int getLiveMatchCount() {
        return liveMatches.size();
    }

    public Map<String, Object> getHealthDetails() {
        return Map.of(
                "isHealthy", isHealthy(),
                "liveMatches", liveMatches.size(),
                "consecutiveFailures", consecutiveFailures,
                "lastSuccessfulUpdate", lastSuccessfulUpdate,
                "timeSinceLastUpdate", System.currentTimeMillis() - lastUpdateTime,
                "pollingMode", adaptivePolling.getPollingMode(),
                "currentPollingInterval", adaptivePolling.getCurrentInterval(),
                "consecutiveEmptyPolls", adaptivePolling.getConsecutiveEmptyPolls(),
                "estimatedHourlyDbConnections", adaptivePolling.calculateHourlyDbConnections()
        );
    }

    public void forceRefresh() {
        log.info("Forcing manual refresh of match data");
        meterRegistry.counter("match.operations", "type", "manual_refresh").increment();

        adaptivePolling.reset();

        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel(false);
        }

        updateMatches();
        scheduleNextUpdate();
    }

    public void clearCache() {
        log.info("Clearing all cached data");
        streamLinkCache.clear();
        failureCount.clear();
        lastUpdateTimes.clear();

        adaptivePolling.reset();

        meterRegistry.counter("match.operations", "type", "cache_clear").increment();
    }
}