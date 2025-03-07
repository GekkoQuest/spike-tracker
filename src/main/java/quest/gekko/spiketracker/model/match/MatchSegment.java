package quest.gekko.spiketracker.model.match;

import lombok.Data;

@Data
public class MatchSegment {
    private String team1;
    private String team2;

    private String score1;
    private String score2;

    private String flag1;
    private String flag2;

    private String time_until_match;

    private String match_series;
    private String match_event;

    private String unix_timestamp;

    private String match_page;
}
