package quest.gekko.spiketracker.model.match;

import java.time.LocalDateTime;

public record MatchHistory(
        String team1,
        String team2,
        String flag1,
        String flag2,
        String team1_logo,
        String team2_logo,
        String finalScore1,
        String finalScore2,
        String match_event,
        String match_series,
        String current_map,
        LocalDateTime completedAt,
        String match_page,
        String streamLink,
        long durationMinutes
) {

    public static MatchHistory fromMatchSegment(MatchSegment segment, long durationMinutes) {
        return new MatchHistory(
                segment.team1(),
                segment.team2(),
                segment.flag1(),
                segment.flag2(),
                segment.team1_logo(),
                segment.team2_logo(),
                segment.score1(),
                segment.score2(),
                segment.match_event(),
                segment.match_series(),
                segment.current_map(),
                LocalDateTime.now(),
                segment.match_page(),
                segment.streamLink(),
                durationMinutes
        );
    }

    public String getWinner() {
        try {
            int score1 = Integer.parseInt(finalScore1);
            int score2 = Integer.parseInt(finalScore2);

            if (score1 > score2) return team1;
            if (score2 > score1) return team2;
            return "Draw";
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }
}