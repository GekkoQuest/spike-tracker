package quest.gekko.spiketracker.discord;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DiscordInitializer implements CommandLineRunner {
    @Value("${discord.token}")
    private String token;
    private final DiscordBot listener;

    public DiscordInitializer(final DiscordBot listener) {
        this.listener = listener;
    }

    @Override
    public void run(final String... args) throws Exception {
        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(listener)
                .build()
                .awaitReady();
    }
}