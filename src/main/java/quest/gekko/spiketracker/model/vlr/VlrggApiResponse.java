package quest.gekko.spiketracker.model.vlr;

import lombok.Data;
import quest.gekko.spiketracker.model.match.LiveMatchData;

@Data
public class VlrggApiResponse {
    private LiveMatchData data;
}
