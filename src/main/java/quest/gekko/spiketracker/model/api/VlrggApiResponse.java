package quest.gekko.spiketracker.model.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import quest.gekko.spiketracker.model.match.LiveMatchData;

public record VlrggApiResponse(@JsonProperty("data") LiveMatchData data) {}