package quest.gekko.spiketracker.service;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.util.HashMap;
import java.util.Map;

@Service
public class MatchTrackingService {
    private final VlrggMatchApiClient apiClient;
    private TextChannel discordChannel;
    private final Map<String, MatchSegment> liveMatches = new HashMap<>();

    public MatchTrackingService(final VlrggMatchApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Scheduled(fixedRate = 3000)
    public void fetchLiveMatchData() {
        if (discordChannel == null) {
            return;
        }

        final LiveMatchData liveMatchData = apiClient.getLiveMatchData();
        if (liveMatchData != null && liveMatchData.getSegments() != null) {
            liveMatchData.getSegments().forEach(this::processMatchSegment);
            removeFinishedMatches(liveMatchData);
        }
    }

    private void processMatchSegment(final MatchSegment segment) {
        final String matchId = segment.getMatch_page();

        if (!liveMatches.containsKey(matchId)) {
            liveMatches.put(matchId, segment);
            discordChannel.sendMessage("New live match detected: " + formatMatchMessage(segment)).queue();
        } else {
            liveMatches.put(matchId, segment);
        }
    }

    private void removeFinishedMatches(final LiveMatchData liveMatchData) {
        liveMatches.entrySet().removeIf(entry -> {
            final String matchId = entry.getKey();
            final boolean isMatchFinished = liveMatchData.getSegments().stream().noneMatch(segment -> segment.getMatch_page().equals(matchId));

            if (isMatchFinished) {
                discordChannel.sendMessage("Match has finished: " + formatMatchMessage(entry.getValue())).queue();
                return true;
            }
            return false;
        });
    }

    private String formatMatchMessage(final MatchSegment segment) {
        return String.format("**%s vs %s**\nEvent: %s\nSeries: %s\nTime: %s",
                segment.getTeam1(), segment.getTeam2(),
                segment.getMatch_event(), segment.getMatch_series(),
                segment.getTime_until_match());
    }

    public void setChannel(final TextChannel discordChannel) {
        this.discordChannel = discordChannel;
    }
}