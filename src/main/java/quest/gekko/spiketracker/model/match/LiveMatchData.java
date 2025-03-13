package quest.gekko.spiketracker.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LiveMatchData(
        @JsonProperty("status") int status,
        @JsonProperty("segments") List<MatchSegment> segments
) {}