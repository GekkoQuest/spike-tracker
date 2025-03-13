package quest.gekko.spiketracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MatchTrackerConfig {

    @Bean
    public RestClient restClient(final RestClient.Builder builder) {
        return builder.build();
    }

}
