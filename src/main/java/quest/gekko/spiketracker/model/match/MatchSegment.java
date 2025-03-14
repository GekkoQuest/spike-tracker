package quest.gekko.spiketracker.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;

// Reference: https://vlrggapi.vercel.app/#/default/VLR_match_match_get

public record MatchSegment(
        @JsonProperty("team1") String team1,
        @JsonProperty("team2") String team2,
        @JsonProperty("flag1") String flag1,
        @JsonProperty("flag2") String flag2,
        @JsonProperty("team1_logo") String team1_logo,
        @JsonProperty("team2_logo") String team2_logo,
        @JsonProperty("score1") String score1,
        @JsonProperty("score2") String score2,
        @JsonProperty("team1_round_ct") String team1_round_ct,
        @JsonProperty("team1_round_t") String team1_round_t,
        @JsonProperty("team2_round_ct") String team2_round_ct,
        @JsonProperty("team2_round_t") String team2_round_t,
        @JsonProperty("map_number") String map_number,
        @JsonProperty("current_map") String current_map,
        @JsonProperty("time_until_match") String time_until_match,
        @JsonProperty("match_event") String match_event,
        @JsonProperty("match_series") String match_series,
        @JsonProperty("unix_timestamp") String unix_timestamp,
        @JsonProperty("match_page") String match_page,
        String streamLink // Scraped later. No annotation needed since not in JSON.
) {

    public MatchSegment withStreamLink(String streamLink) {
        return new MatchSegment(
                this.team1, this.team2,
                this.flag1, this.flag2,
                this.team1_logo, this.team2_logo,
                this.score1, this.score2,
                this.team1_round_ct, this.team1_round_t,
                this.team2_round_ct, this.team2_round_t,
                this.map_number, this.current_map,
                this.time_until_match,
                this.match_event, this.match_series,
                this.unix_timestamp,
                this.match_page, streamLink
        );
    }

}