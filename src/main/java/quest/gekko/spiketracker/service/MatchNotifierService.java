package quest.gekko.spiketracker.service;

import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MatchNotifierService {
    private final Map<String, String> matchToMessage = new ConcurrentHashMap<>();
    private TextChannel discordChannel;

    public void setChannel(final TextChannel discordChannel) {
        this.discordChannel = discordChannel;
    }

    public void notifyNewMatch(final MatchSegment segment) {
        if (discordChannel == null) {
            log.warn("Discord channel not set. Cannot send match update.");
            return;
        }

        final MessageEmbed embed = createMatchEmbed(segment, false);

        discordChannel.sendMessageEmbeds(embed).queue(
                message -> matchToMessage.put(segment.match_page(), message.getId())
        );
    }

    public void notifyScoreUpdate(final MatchSegment segment) {
        if (discordChannel == null)
            return;

        final String messageId = matchToMessage.get(segment.match_page());

        if (messageId != null) {
            discordChannel.editMessageEmbedsById(messageId, createMatchEmbed(segment, false)).queue();
        }
    }

    public void notifyMatchCompletion(final MatchSegment segment) {
        if (discordChannel == null)
            return;

        final String messageId = matchToMessage.get(segment.match_page());

        if (messageId != null) {
            discordChannel.editMessageEmbedsById(messageId, createMatchEmbed(segment, true)).queue();
        }
    }

    private MessageEmbed createMatchEmbed(final MatchSegment segment, final boolean completed) {
        return new EmbedBuilder()
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
                .setUrl(segment.streamLink())
                .build();
    }
}
