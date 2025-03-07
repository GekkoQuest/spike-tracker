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
    private final Map<String, String> matchMessages = new HashMap<>(); // Tracks matchId -> messageId

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
        updateCompletedMatches(liveMatchData);
    }

    private void processMatchSegment(final MatchSegment segment) {
        final String matchId = segment.getMatch_page();

        // Update match data and its embed if it already exists.
        if (liveMatches.containsKey(matchId)) {
            liveMatches.put(matchId, segment);
            updateMatchEmbed(segment, matchMessages.get(matchId), false);
            return;
        }

        // Otherwise store match data and generate a fresh embed.
        liveMatches.put(matchId, segment);
        sendMatchEmbed(segment);
    }

    private void sendMatchEmbed(final MatchSegment segment) {
        final MessageEmbed embed = createMatchEmbed(segment, false);

        discordChannel.sendMessageEmbeds(embed).queue(message ->
                matchMessages.put(segment.getMatch_page(), message.getId()) // Store message id to update later.
        );
    }

    private void updateMatchEmbed(final MatchSegment segment, final String messageId, boolean isCompleted) {
        if (messageId == null)
            return; // No message to update

        final MessageEmbed updatedEmbed = createMatchEmbed(segment, isCompleted);
        discordChannel.editMessageEmbedsById(messageId, updatedEmbed).queue();
    }

    private void updateCompletedMatches(final LiveMatchData liveMatchData) {
        liveMatches.entrySet().removeIf(entry -> {
            final String matchId = entry.getKey();
            final boolean isMatchFinished = liveMatchData.getSegments().stream().noneMatch(segment -> segment.getMatch_page().equals(matchId));

            if (isMatchFinished) {
                updateMatchEmbed(entry.getValue(), matchMessages.get(matchId), true);
                matchMessages.remove(matchId); // Remove match data from memory as it's no longer necessary to track.
                return true;
            }
            return false;
        });
    }

    private MessageEmbed createMatchEmbed(final MatchSegment segment, boolean isCompleted) {
        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle(isCompleted ? "Completed Valorant Match" : "Live Valorant Match")
                .setDescription(formatMatchDescription(segment))
                .addField("Event", "**" + segment.getMatch_event() + "**\n\u200B", false)
                .addField("Series", "**" + segment.getMatch_series() + "**\n\u200B", false)
                .addField("Score", "**" + segment.getScore1() + " - " + segment.getScore2() + "**", false)
                .setColor(isCompleted ? Color.GRAY : Color.RED)
                .setFooter(isCompleted ? "Match completed" : "Live match updates", null);

        return embedBuilder.build();
    }

    private String formatMatchDescription(final MatchSegment segment) {
        return String.format("%s %s **vs** %s %s\n\u200B",
                ":" + segment.getFlag1() + ":", segment.getTeam1(),
                segment.getTeam2(), ":" + segment.getFlag2() + ":");
    }

    public void setChannel(final TextChannel discordChannel) {
        this.discordChannel = discordChannel;
    }
}