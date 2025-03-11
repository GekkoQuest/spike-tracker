package quest.gekko.spiketracker.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MatchTrackingService {
    private final VlrggMatchApiClient apiClient;
    private TextChannel discordChannel;

    @Getter
    private final Map<String, MatchSegment> liveMatches = new HashMap<>();
    private final Map<String, String> matchMessages = new HashMap<>(); // Tracks matchId -> messageId
    @Getter
    private final Map<String, String> matchStreamLinks = new HashMap<>(); // Tracks matchId -> stream link

    public MatchTrackingService(final VlrggMatchApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Scheduled(fixedRate = 5000) // Check for updates every 5 seconds
    public void fetchLiveMatchData() {
        if (discordChannel == null) {
            log.warn("Live match tracking is disabled until a Discord channel is set.");
            return;
        }

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
            final MatchSegment previousSegment = liveMatches.get(matchId);

            // Only update if score has changed. Helps prevent 429 rate limit error as well.
            if (hasScoreChanged(previousSegment, segment)) {
                log.info("Updating match: {}", matchId);
                liveMatches.put(matchId, segment);
                updateMatchEmbed(segment, matchMessages.get(matchId), false);
            }

            return;
        }

        // Otherwise store match data and generate a fresh embed.
        log.info("New match detected: {}", matchId);
        liveMatches.put(matchId, segment);

        final String streamLink = scrapeStreamLink(segment.getMatch_page());
        if (streamLink != null) {
            segment.setStreamLink(streamLink); // Set the stream link in the MatchSegment object
            matchStreamLinks.put(segment.getMatch_page(), streamLink);
        }

        sendMatchEmbed(segment);
    }

    private String scrapeStreamLink(final String matchUrl) {
        try {
            // Get HTML content of webpage.
            final Document document = Jsoup.connect(matchUrl).get();

            // Locate where stream links are being stored.
            final Element streamContainer = document.selectFirst("div.match-streams-container");

            if (streamContainer != null) {
                // Find the first available stream link.
                final Element streamLinkElement = streamContainer.selectFirst("a[href]");

                if (streamLinkElement != null) {
                    final String streamLink = streamLinkElement.attr("href");
                    log.info("Scraped stream link for match {}: {}", matchUrl, streamLink); // Debugging log
                    return streamLink;
                }
            }
        } catch (final IOException e) {
            log.error("Failed to scrape stream link for match: {}", matchUrl, e);
        }

        // Should rarely, if ever, happen. Generally VLR matches contain a stream link.
        log.warn("No stream link found for match: {}", matchUrl);
        return null;
    }

    private boolean hasScoreChanged(final MatchSegment previousSegment, final MatchSegment newSegment) {
        return !previousSegment.getScore1().equals(newSegment.getScore1()) ||
                !previousSegment.getScore2().equals(newSegment.getScore2());
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
                matchStreamLinks.remove(matchId);
                return true;
            }
            return false;
        });
    }

    private MessageEmbed createMatchEmbed(final MatchSegment segment, boolean isCompleted) {
        final EmbedBuilder embedBuilder = new EmbedBuilder()
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
        log.info("Discord channel set to '{}'", discordChannel.getName());
    }
}