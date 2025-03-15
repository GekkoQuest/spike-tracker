package quest.gekko.spiketracker.service;

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
import quest.gekko.spiketracker.service.api.VlrggMatchApiClient;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchTrackingService {
    private final VlrggMatchApiClient apiClient;
    private TextChannel discordChannel;

    private final Map<String, String> matchToMessage = new ConcurrentHashMap<>();
    private final Map<String, MatchSegment> liveMatches = new ConcurrentHashMap<>();
    private final Map<String, String> streamLinkCache = new ConcurrentHashMap<>();

    public MatchTrackingService(final VlrggMatchApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Scheduled(fixedRate = 5000)
    public void updateMatches() {
        if (discordChannel == null) {
            log.warn("Discord channel not set yet. Skipping update.");
            return;
        }

        final List<MatchSegment> currentSegments = Optional.ofNullable(apiClient.getLiveMatchData())
                .map(LiveMatchData::segments)
                .orElseGet(() -> {
                    log.warn("No match segments retrieved.");
                    return Collections.emptyList();
                });

        final Set<String> currentMatchIds = currentSegments.stream()
                .map(MatchSegment::match_page)
                .collect(Collectors.toSet());

        liveMatches.keySet().stream()
                .filter(matchId -> !currentMatchIds.contains(matchId))
                .forEach(this::handleCompletedMatch);

        // Process new or existing matches
        currentSegments.forEach(this::handleNewOrUpdate);
    }

    private void handleNewOrUpdate(final MatchSegment segment) {
        final String matchId = segment.match_page();
        final MatchSegment previousSegment = liveMatches.get(matchId);

        if (previousSegment == null) {
            handleNewMatch(segment, matchId);
        } else {
            handleScoreUpdate(segment, previousSegment, matchId);
        }
    }

    private void handleNewMatch(MatchSegment segment, final String matchId) {
        final String streamLink = scrapeStreamLink(matchId);
        streamLinkCache.put(matchId, streamLink);

        segment = segment.withStreamLink(streamLink);

        liveMatches.put(matchId, segment);

        final String existingMessageId = matchToMessage.get(matchId);
        if (existingMessageId == null) {
            sendMatchEmbed(segment);
        } else {
            log.debug("Match already exists for {}", matchId);
        }
    }

    private void handleScoreUpdate(MatchSegment segment, final MatchSegment previousSegment, final String matchId) {
        if (hasScoreChanged(previousSegment, segment)) {
            final String cachedStreamLink = streamLinkCache.get(matchId);
            if (cachedStreamLink != null && (segment.streamLink() == null || segment.streamLink().isEmpty())) {
                segment = segment.withStreamLink(cachedStreamLink);
            }

            liveMatches.put(matchId, segment);

            final String existingMessageId = matchToMessage.get(matchId);
            if (existingMessageId != null) {
                updateMatchEmbed(segment, existingMessageId, false);
            }
        }
    }

    private boolean hasScoreChanged(final MatchSegment oldSegment, final MatchSegment newSegment) {
        return !oldSegment.score1().equals(newSegment.score1()) ||
                !oldSegment.score2().equals(newSegment.score2());
    }

    private void handleCompletedMatch(final String matchId) {
        log.info("Handling completed match {}", matchId);
        final String messageId = matchToMessage.remove(matchId);
        final MatchSegment completedSegment = liveMatches.remove(matchId);

        if (messageId != null && completedSegment != null) {
            log.info("Match completed: {}", matchId);
            updateMatchEmbed(completedSegment, messageId, true);
        }
    }

    private String scrapeStreamLink(final String matchUrl) {
        try {
            final Document doc = Jsoup.connect(matchUrl).get();
            final Element streamLinkElement = doc.selectFirst("div.match-streams-container a[href]");

            if (streamLinkElement != null) {
                final String streamLink = streamLinkElement.attr("href");
                log.info("✅ Scraped stream link: {}", streamLink);
                return streamLink;
            } else {
                log.warn("⚠️ Stream link HTML element NOT found");
            }
        } catch (final IOException e) {
            log.error("⚠️ Jsoup scrape failed for {}", matchUrl, e);
        }
        return null;
    }

    private void sendMatchEmbed(final MatchSegment segment) {
        final MessageEmbed embed = createMatchEmbed(segment, false);
        discordChannel.sendMessageEmbeds(embed).queue(message ->
                matchToMessage.put(segment.match_page(), message.getId())
        );
    }

    private void updateMatchEmbed(final MatchSegment segment, final String messageId, final boolean completed) {
        discordChannel.editMessageEmbedsById(messageId, createMatchEmbed(segment, completed)).queue();
    }

    private MessageEmbed createMatchEmbed(final MatchSegment segment, boolean completed) {
        final EmbedBuilder embed = new EmbedBuilder()
                .setTitle(completed ? "🏁 Match Completed" : "🏁 Live Match - Click to Watch")
                .setDescription(String.format(":%s: **%s** vs **%s** :%s:",
                        segment.flag1(), segment.team1(), segment.team2(), segment.flag2()))
                .addField("Event", segment.match_event(), false)
                .addField("Series", segment.match_series(), false)
                .addField(completed ? "Final Score" : "Current Score",
                        segment.score1() + " - " + segment.score2(), false)
                .setColor(completed ? Color.GRAY : Color.RED)
                .setFooter(completed
                        ? "✅ Match ended. Final results."
                        : "🎮 Live updates ongoing.", null)
                .setUrl(segment.streamLink());

        return embed.build();
    }

    public void setChannel(final TextChannel discordChannel) {
        this.discordChannel = discordChannel;
        log.info("Discord updates will now be sent to '{}'", discordChannel.getName());
    }

    public Map<String, MatchSegment> getLiveMatches() {
        return Map.copyOf(liveMatches);
    }
}