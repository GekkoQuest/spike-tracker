package quest.gekko.spiketracker.discord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import quest.gekko.spiketracker.discord.command.base.CommandManager;

@Component
public class DiscordInitializer implements CommandLineRunner {
    @Value("${discord.token}")
    private String token;
    private final CommandManager commandManager;

    public DiscordInitializer(final CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void run(final String... args) throws Exception {
        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(commandManager)
                .build()
                .awaitReady();
    }
}