package quest.gekko.spiketracker.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.awt.*;
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

    @Scheduled(fixedRate = 3000) // Update every 3 seconds
    public void fetchLiveMatchData() {
        if (discordChannel == null)
            return;

        final LiveMatchData liveMatchData = apiClient.getLiveMatchData();
        if (liveMatchData == null || liveMatchData.getSegments() == null)
            return;

        liveMatchData.getSegments().forEach(this::processMatchSegment);
        removeCompletedMatches(liveMatchData);
    }

    private void processMatchSegment(final MatchSegment segment) {
        final String matchId = segment.getMatch_page();
        if (liveMatches.containsKey(matchId)) {
            liveMatches.put(matchId, segment);
            return;
        }

        liveMatches.put(matchId, segment);
        sendMatchEmbed(segment, "Live Valorant Match");
    }

    private void removeCompletedMatches(final LiveMatchData liveMatchData) {
        liveMatches.entrySet().removeIf(entry -> {
            final String matchId = entry.getKey();
            boolean isMatchCompleted = liveMatchData.getSegments().stream().noneMatch(segment -> segment.getMatch_page().equals(matchId));

            if (isMatchCompleted) {
                sendMatchEmbed(entry.getValue(), "Completed Valorant Match");
                return true;
            }
            return false;
        });
    }

    private void sendMatchEmbed(final MatchSegment segment, final String title) {
        final MessageEmbed messageEmbed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(formatMatchDescription(segment))
                .addField("🏆 Event", "**" + segment.getMatch_event() + "**\n\u200B", false)
                .addField("🎮 Series", "**" + segment.getMatch_series() + "**", false)
                .setColor(Color.RED)
                .setFooter("Live match updates", null)
                .build();

        discordChannel.sendMessageEmbeds(messageEmbed).queue();
    }

    private String formatMatchDescription(final MatchSegment segment) {
        return String.format(":%s: **%s** vs :%s: **%s**\n\u200B",
                segment.getFlag1(),
                segment.getTeam1(),
                segment.getFlag2(),
                segment.getTeam2()
        );
    }

    public void setChannel(final TextChannel discordChannel) {
        this.discordChannel = discordChannel;
    }
}