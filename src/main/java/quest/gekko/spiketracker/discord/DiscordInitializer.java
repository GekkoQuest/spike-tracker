package quest.gekko.spiketracker.discord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import quest.gekko.spiketracker.config.DiscordProperties;
import quest.gekko.spiketracker.discord.command.base.CommandManager;

@Component
public class DiscordInitializer implements CommandLineRunner {
    private final DiscordProperties properties;
    private final CommandManager commandManager;

    public DiscordInitializer(final DiscordProperties discordProperties,  final CommandManager commandManager) {
        this.properties = discordProperties;
        this.commandManager = commandManager;
    }

    @Override
    public void run(final String... args) throws Exception {
        JDABuilder.createDefault(properties.token())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(commandManager)
                .build()
                .awaitReady();
    }
}