package quest.gekko.spiketracker.controller;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import quest.gekko.spiketracker.service.MatchTrackingService;

@Slf4j
@Service
public class DiscordBot extends ListenerAdapter {
    @Value("${discord.token}")
    private String token;
    private final MatchTrackingService matchTrackingService;

    public DiscordBot(final MatchTrackingService matchTrackingService) {
        this.matchTrackingService = matchTrackingService;
    }

    @PostConstruct
    public void startBot() {
        try {
            JDABuilder.createDefault(token)
                    .addEventListeners(this)
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                    .build()
                    .awaitReady();
            log.info("Discord bot started successfully.");
        } catch (InterruptedException e) {
            log.error("An error has occurred while starting the Discord bot.", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onMessageReceived(final MessageReceivedEvent event) {
        final String content = event.getMessage().getContentRaw();

        if (!content.equalsIgnoreCase("#setchannel"))
            return;


        if (event.getChannel() instanceof TextChannel targetChannel) {
            matchTrackingService.setChannel(targetChannel);
            event.getChannel().sendMessage("Updates will now be posted in this channel.").queue();
        } else {
            event.getChannel().sendMessage("This command can only be used in a text channel.").queue();
        }
    }
}