package quest.gekko.spiketracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

public class ApplicationProperties {
    @ConfigurationProperties(prefix = "vlrgg.api")
    public record VlrggApi(String baseUrl) {}
}