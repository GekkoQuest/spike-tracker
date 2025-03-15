package quest.gekko.spiketracker.service;

import lombok.extern.slf4j.Slf4j;
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
    private final MatchNotifierService notifierService;

    private final Map<String, MatchSegment> liveMatches = new ConcurrentHashMap<>();

    public MatchTrackingService(final VlrggMatchApiClient apiClient, final MatchNotifierService notifierService) {
        this.apiClient = apiClient;
        this.notifierService = notifierService;
    }

    @Scheduled(fixedRate = 5000)
    public void updateMatches() {
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
    }

    private void handleMatchUpdate(final MatchSegment segment) {
        final String matchId = segment.match_page();
        final MatchSegment previousSegment = liveMatches.get(matchId);

        if (previousSegment == null) {
            handleNewMatch(segment, matchId);
        } else if (hasScoreChanged(previousSegment, segment)) {
            handleScoreUpdate(segment, matchId);
        }
    }

    private void handleNewMatch(MatchSegment segment, final String matchId) {
        final String streamLink = StreamLinkScraper.scrapeStreamLink(matchId);
        segment = segment.withStreamLink(streamLink);

        liveMatches.put(matchId, segment);
        notifierService.notifyNewMatch(segment);
    }

    private void handleScoreUpdate(final MatchSegment segment, final String matchId) {
        liveMatches.put(matchId, segment);
        notifierService.notifyScoreUpdate(segment);
    }

    private void handleCompletedMatch(final String matchId) {
        final MatchSegment completedSegment = liveMatches.remove(matchId);

        if (completedSegment != null) {
            notifierService.notifyMatchCompletion(completedSegment);
        }
    }

    private boolean hasScoreChanged(final MatchSegment oldSegment, final MatchSegment newSegment) {
        return !oldSegment.score1().equals(newSegment.score1()) ||
                !oldSegment.score2().equals(newSegment.score2());
    }

    public Map<String, MatchSegment> getLiveMatches() {
        return Map.copyOf(liveMatches);
    }
}