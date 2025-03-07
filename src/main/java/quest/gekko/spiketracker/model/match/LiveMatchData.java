package quest.gekko.spiketracker.model.match;

import lombok.Data;

import java.util.List;

@Data
public class LiveMatchData {
    private int status;
    private List<MatchSegment> segments;
}
