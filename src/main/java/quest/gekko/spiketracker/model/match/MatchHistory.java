package quest.gekko.spiketracker.model.match;

import java.time.LocalDateTime;

public record MatchHistory(
        String team1,
        String team2,
        String flag1,
        String flag2,
        String team1Logo,
        String team2Logo,
        String finalScore1,
        String finalScore2,
        String match_event,
        String match_series,
        String current_map,
        LocalDateTime completedAt,
        String match_page,
        String streamLink,
        long durationMinutes,
        String winner
) { }