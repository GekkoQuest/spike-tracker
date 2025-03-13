package quest.gekko.spiketracker.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vlrgg.api")
public record VlrggApiProperties(String baseUrl) {}