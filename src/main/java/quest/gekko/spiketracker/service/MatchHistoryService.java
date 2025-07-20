package quest.gekko.spiketracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.MatchHistory;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchHistoryService {
    private final Map<String, MatchHistory> completedMatches = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> matchStartTimes = new ConcurrentHashMap<>();

    public void recordMatchStart(final String matchId) {
        matchStartTimes.put(matchId, LocalDateTime.now());
    }

    public void recordMatchCompletion(final MatchSegment segment) {
        final LocalDateTime startTime = matchStartTimes.get(segment.match_page());
        long durationMinutes = 0;

        if (startTime != null) {
            durationMinutes = Duration.between(startTime, LocalDateTime.now()).toMinutes();
            matchStartTimes.remove(segment.match_page());
        }

        final MatchHistory history = MatchHistory.fromMatchSegment(segment, durationMinutes);
        completedMatches.put(segment.match_page(), history);

        log.info("Recorded completed match: {} vs {} (Duration: {} mins)",
                segment.team1(), segment.team2(), durationMinutes);
    }

    public List<MatchHistory> getRecentCompletedMatches(int limit) {
        return completedMatches.values().stream()
                .sorted((a, b) -> b.completedAt().compareTo(a.completedAt()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public Optional<MatchHistory> getMatchHistory(final String matchId) {
        return Optional.ofNullable(completedMatches.get(matchId));
    }

    public Map<String, Long> getTeamStats() {
        return completedMatches.values().stream()
                .flatMap(match -> Arrays.stream(new String[]{match.team1(), match.team2()}))
                .collect(Collectors.groupingBy(
                        team -> team,
                        Collectors.counting()
                ));
    }
}