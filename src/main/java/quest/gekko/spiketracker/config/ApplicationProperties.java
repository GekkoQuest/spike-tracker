package quest.gekko.spiketracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

public class ApplicationProperties {

    @ConfigurationProperties(prefix = "discord")
    public record Discord(String token) {}

    @ConfigurationProperties(prefix = "vlrgg.api")
    public record VlrggApi(String baseUrl) {}
}