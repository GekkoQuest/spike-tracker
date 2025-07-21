package quest.gekko.spiketracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import quest.gekko.spiketracker.entity.MatchHistoryEntity;
import quest.gekko.spiketracker.entity.MatchTrackingEntity;
import quest.gekko.spiketracker.model.match.MatchHistory;
import quest.gekko.spiketracker.model.match.MatchSegment;
import quest.gekko.spiketracker.repository.MatchHistoryRepository;
import quest.gekko.spiketracker.repository.MatchTrackingRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchHistoryService {
    private final MatchHistoryRepository matchHistoryRepository;
    private final MatchTrackingRepository matchTrackingRepository;

    @Transactional
    public void recordMatchStart(final String matchId, final MatchSegment segment) {
        try {
            if (matchTrackingRepository.existsByMatchId(matchId)) {
                log.debug("Match {} is already being tracked", matchId);
                return;
            }

            final MatchTrackingEntity trackingEntity = MatchTrackingEntity.builder()
                    .matchId(matchId)
                    .team1(segment.team1())
                    .team2(segment.team2())
                    .startTime(LocalDateTime.now())
                    .status(MatchTrackingEntity.MatchStatus.LIVE)
                    .lastScore1(segment.score1())
                    .lastScore2(segment.score2())
                    .currentMap(segment.current_map())
                    .matchEvent(segment.match_event())
                    .streamLink(segment.streamLink())
                    .build();

            matchTrackingRepository.save(trackingEntity);
            log.info("Started tracking match: {} vs {} (ID: {})", segment.team1(), segment.team2(), matchId);
        } catch (final Exception e) {
            log.error("Failed to record match start for {}: {}", matchId, e.getMessage(), e);
        }
    }

    @Transactional
    @CacheEvict(value = "matchHistory", allEntries = true)
    public void recordMatchCompletion(final MatchSegment segment) {
        try {
            final String matchId = segment.match_page();

            if (matchHistoryRepository.existsByMatchPage(matchId)) {
                log.debug("Match {} already recorded in history", matchId);
                return;
            }

            long durationMinutes = 0;
            final Optional<MatchTrackingEntity> tracking = matchTrackingRepository.findByMatchId(matchId);

            if (tracking.isPresent()) {
                LocalDateTime startTime = tracking.get().getStartTime();
                durationMinutes = Duration.between(startTime, LocalDateTime.now()).toMinutes();
                matchTrackingRepository.updateMatchStatus(matchId, MatchTrackingEntity.MatchStatus.COMPLETED);
            }

            final MatchHistoryEntity historyEntity = MatchHistoryEntity.builder()
                    .team1(segment.team1())
                    .team2(segment.team2())
                    .flag1(segment.flag1())
                    .flag2(segment.flag2())
                    .team1Logo(segment.team1_logo())
                    .team2Logo(segment.team2_logo())
                    .finalScore1(segment.score1())
                    .finalScore2(segment.score2())
                    .matchEvent(segment.match_event())
                    .matchSeries(segment.match_series())
                    .currentMap(segment.current_map())
                    .matchPage(matchId)
                    .streamLink(segment.streamLink())
                    .durationMinutes(durationMinutes)
                    .completedAt(LocalDateTime.now())
                    .build();

            matchHistoryRepository.save(historyEntity);

            log.info("Recorded completed match: {} vs {} (Duration: {} mins, Final: {}-{})",
                    segment.team1(), segment.team2(), durationMinutes,
                    segment.score1(), segment.score2());
        } catch (final Exception e) {
            log.error("Failed to record match completion for {}: {}",
                    segment.match_page(), e.getMessage(), e);
        }
    }

    @Cacheable(value = "matchHistory", key = "#limit")
    @Transactional(readOnly = true)
    public List<MatchHistory> getRecentCompletedMatches(final int limit) {
        try {
            return matchHistoryRepository.findTop20ByOrderByCompletedAtDesc()
                    .stream()
                    .limit(limit)
                    .map(this::convertToMatchHistory)
                    .collect(Collectors.toList());
        } catch (final Exception e) {
            log.error("Failed to retrieve recent matches: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public Optional<MatchHistory> getMatchHistory(final String matchId) {
        try {
            return matchHistoryRepository.findByMatchPage(matchId).map(this::convertToMatchHistory);
        } catch (final Exception e) {
            log.error("Failed to retrieve match history for {}: {}", matchId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Cacheable(value = "teamStats")
    @Transactional(readOnly = true)
    public Map<String, Long> getTeamStats() {
        try {
            final List<MatchHistoryEntity> allMatches = matchHistoryRepository.findAll();
            return allMatches.stream()
                    .flatMap(match -> Stream.of(match.getTeam1(), match.getTeam2()))
                    .collect(Collectors.groupingBy(team -> team, Collectors.counting()));
        } catch (final Exception e) {
            log.error("Failed to calculate team stats: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    @Transactional(readOnly = true)
    public List<MatchHistory> getMatchesForTeam(final String teamName, final int limit) {
        try {
            final Pageable pageable = PageRequest.of(0, Math.min(limit, 50));
            return matchHistoryRepository.findMatchesForTeam(teamName, pageable)
                    .stream()
                    .map(this::convertToMatchHistory)
                    .collect(Collectors.toList());
        } catch (final Exception e) {
            log.error("Failed to retrieve matches for team {}: {}", teamName, e.getMessage(), e);
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public Double getAverageMatchDuration() {
        try {
            return matchHistoryRepository.getAverageMatchDuration();
        } catch (final Exception e) {
            log.error("Failed to calculate average match duration: {}", e.getMessage(), e);
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<Object[]> getEventStatistics() {
        try {
            return matchHistoryRepository.getEventStatistics();
        } catch (final Exception e) {
            log.error("Failed to retrieve event statistics: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @Async
    @Transactional
    public void updateMatchScore(final String matchId, final String score1, final String score2, final String currentMap, final String streamLink) {
        try {
            matchTrackingRepository.updateMatchDetails(matchId, score1, score2, currentMap, streamLink);
        } catch (final Exception e) {
            log.error("Failed to update match score for {}: {}", matchId, e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 3600000) // 1 hour
    @Transactional
    @CacheEvict(value = {"matchHistory", "teamStats"}, allEntries = true)
    public void cleanupOldRecords() {
        try {
            final LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

            matchTrackingRepository.deleteByStatusAndStartTimeBefore(MatchTrackingEntity.MatchStatus.COMPLETED, cutoffDate);

            LocalDateTime historyCutoff = LocalDateTime.now().minusYears(1);
            matchHistoryRepository.deleteByCompletedAtBefore(historyCutoff);

            log.info("Cleaned up old tracking and history records");
        } catch (final Exception e) {
            log.error("Failed to cleanup old records: {}", e.getMessage(), e);
        }
    }

    private MatchHistory convertToMatchHistory(final MatchHistoryEntity entity) {
        return new MatchHistory(
                entity.getTeam1(),
                entity.getTeam2(),
                entity.getFlag1(),
                entity.getFlag2(),
                entity.getTeam1Logo(),
                entity.getTeam2Logo(),
                entity.getFinalScore1(),
                entity.getFinalScore2(),
                entity.getMatchEvent(),
                entity.getMatchSeries(),
                entity.getCurrentMap(),
                entity.getCompletedAt(),
                entity.getMatchPage(),
                entity.getStreamLink(),
                entity.getDurationMinutes() != null ? entity.getDurationMinutes() : 0
        );
    }
}