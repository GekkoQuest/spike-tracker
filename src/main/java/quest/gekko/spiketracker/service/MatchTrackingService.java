package quest.gekko.spiketracker.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.service.api.VlrggMatchApiClient;
import quest.gekko.spiketracker.util.StreamLinkScraper;

import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchTrackingService {
    private final VlrggMatchApiClient apiClient;
    private final MatchHistoryService historyService;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamLinkScraper streamLinkScraper;

    private final Map<String, MatchSegment> liveMatches = new ConcurrentHashMap<>();
    private final Map<String, String> streamLinkCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();

    @Getter
    private long lastUpdateTime = System.currentTimeMillis();
    private boolean isHealthy = true;

    public MatchTrackingService(final VlrggMatchApiClient apiClient,
                                final MatchHistoryService historyService,
                                final SimpMessagingTemplate messagingTemplate,
                                final StreamLinkScraper streamLinkScraper) {
        this.apiClient = apiClient;
        this.historyService = historyService;
        this.messagingTemplate = messagingTemplate;
        this.streamLinkScraper = streamLinkScraper;
    }

    @Scheduled(fixedRate = 5000)
    public void updateMatches() {
        try {
            final List<MatchSegment> currentMatches = Optional.ofNullable(apiClient.getLiveMatchData())
                    .map(LiveMatchData::segments)
                    .orElseGet(Collections::emptyList);

            final Set<String> currentMatchIds = currentMatches.stream()
                    .map(MatchSegment::match_page)
                    .collect(Collectors.toSet());

            // Handle completed matches
            liveMatches.keySet().stream()
                    .filter(matchId -> !currentMatchIds.contains(matchId))
                    .forEach(this::handleCompletedMatch);

            // Handle new or updated matches
            currentMatches.forEach(this::handleMatchUpdate);

            // Broadcast to WebSocket subscribers
            messagingTemplate.convertAndSend("/topic/matches", liveMatches.values());

            lastUpdateTime = System.currentTimeMillis();
            isHealthy = true;
            failureCount.clear();
        } catch (Exception e) {
            log.error("Error during match update cycle", e);
            isHealthy = false;

            // Increment failure count for each match
            liveMatches.keySet().forEach(matchId -> failureCount.merge(matchId, 1, Integer::sum));
        }
    }

    private void handleMatchUpdate(final MatchSegment segment) {
        final String matchId = segment.match_page();
        final MatchSegment previousSegment = liveMatches.get(matchId);

        if (previousSegment == null) {
            log.info("New match detected: {} vs {} ({})",
                    segment.team1(), segment.team2(), matchId);
            handleNewMatch(segment, matchId);
            return;
        }

        if (hasScoreChanged(previousSegment, segment)) {
            log.info("Score updated in {}: {} vs {} ({}-{})",
                    matchId, segment.team1(), segment.team2(),
                    segment.score1(), segment.score2());
            handleScoreUpdate(segment, matchId);
        }
    }

    private void handleNewMatch(final MatchSegment segment, final String matchId) {
        historyService.recordMatchStart(matchId);

        // Scrape stream link in background to avoid blocking
        new Thread(() -> {
            try {
                final String streamLink = streamLinkScraper.scrapeStreamLink(segment.match_page());
                if (streamLink != null) {
                    streamLinkCache.put(matchId, streamLink);

                    // Update the match with stream link
                    final MatchSegment updatedSegment = segment.withStreamLink(streamLink);
                    liveMatches.put(matchId, updatedSegment);

                    // Broadcast update
                    messagingTemplate.convertAndSend("/topic/matches", liveMatches.values());
                }
            } catch (Exception e) {
                log.warn("Failed to scrape stream link for {}", matchId, e);
            }
        }).start();

        liveMatches.put(matchId, segment);
    }

    private void handleScoreUpdate(MatchSegment segment, final String matchId) {
        final String cachedStreamLink = streamLinkCache.get(matchId);

        if ((segment.streamLink() == null || segment.streamLink().isEmpty()) && cachedStreamLink != null) {
            segment = segment.withStreamLink(cachedStreamLink);
        }

        liveMatches.put(matchId, segment);
    }

    private void handleCompletedMatch(final String matchId) {
        final MatchSegment completedSegment = liveMatches.remove(matchId);

        if (completedSegment != null) {
            log.info("Match completed: {} vs {} (Final: {}-{})",
                    completedSegment.team1(), completedSegment.team2(),
                    completedSegment.score1(), completedSegment.score2());

            historyService.recordMatchCompletion(completedSegment);
        }

        streamLinkCache.remove(matchId);
        failureCount.remove(matchId);
    }

    private boolean hasScoreChanged(final MatchSegment oldSegment, final MatchSegment newSegment) {
        return !oldSegment.score1().equals(newSegment.score1()) ||
                !oldSegment.score2().equals(newSegment.score2()) ||
                !Objects.equals(oldSegment.team1_round_ct(), newSegment.team1_round_ct()) ||
                !Objects.equals(oldSegment.team1_round_t(), newSegment.team1_round_t()) ||
                !Objects.equals(oldSegment.team2_round_ct(), newSegment.team2_round_ct()) ||
                !Objects.equals(oldSegment.team2_round_t(), newSegment.team2_round_t());
    }

    public Map<String, MatchSegment> getLiveMatches() {
        return Map.copyOf(liveMatches);
    }

    public boolean isHealthy() {
        return isHealthy && (System.currentTimeMillis() - lastUpdateTime) < 30000; // 30 seconds
    }

    public int getLiveMatchCount() {
        return liveMatches.size();
    }
}