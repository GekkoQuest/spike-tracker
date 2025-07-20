package quest.gekko.spiketracker.service.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import quest.gekko.spiketracker.config.ApplicationProperties;
import quest.gekko.spiketracker.model.api.VlrggApiResponse;
import quest.gekko.spiketracker.model.match.LiveMatchData;

@Slf4j
@Service
public class VlrggMatchApiClient {
    private final RestTemplate restTemplate;
    private final ApplicationProperties.VlrggApi properties;

    public VlrggMatchApiClient(final ApplicationProperties.VlrggApi properties) {
        this.restTemplate = new RestTemplate();
        this.properties = properties;
    }

    public LiveMatchData getLiveMatchData() {
        try {
            final VlrggApiResponse response = restTemplate.getForObject(
                    properties.baseUrl() + "/match?q=live_score",
                    VlrggApiResponse.class
            );

            // log.info("API response: {}", response);
            return response != null ? response.data() : null;
        } catch (final Exception e) {
            log.error("Error retrieving live matches", e);
            return null;
        }
    }
}