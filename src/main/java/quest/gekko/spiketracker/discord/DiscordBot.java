package quest.gekko.spiketracker.discord;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;
import quest.gekko.spiketracker.service.MatchTrackingService;

@Component
public class DiscordBot extends ListenerAdapter {
    private final MatchTrackingService matchTrackingService;

    public DiscordBot(final MatchTrackingService matchTrackingService) {
        this.matchTrackingService = matchTrackingService;
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        if (event.getAuthor().isBot())
            return;

        if (!"#setchannel".equalsIgnoreCase(event.getMessage().getContentRaw()))
            return;

        if (event.getChannel() instanceof TextChannel channel) {
            matchTrackingService.setChannel(channel);
            channel.sendMessage("Channel has been set for match updates!").queue();
        }
    }
}