package quest.gekko.spiketracker.service.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import quest.gekko.spiketracker.config.properties.VlrggApiProperties;
import quest.gekko.spiketracker.model.api.VlrggApiResponse;
import quest.gekko.spiketracker.model.match.LiveMatchData;

import java.util.Optional;

@Slf4j
@Service
public class VlrggMatchApiClient {
    private final RestClient client;
    private final VlrggApiProperties props;

    public VlrggMatchApiClient(final RestClient client, final VlrggApiProperties props) {
        this.client = client;
        this.props = props;
    }

    public LiveMatchData getLiveMatchData() {
        try {
            final VlrggApiResponse response = client.get()
                    .uri(props.baseUrl() + "/match?q=live_score")
                    .retrieve()
                    .body(VlrggApiResponse.class);

            log.info("API response: {}", response);
            return response != null ? response.data() : null;
        } catch (final Exception e) {
            log.error("Error retrieving live matches", e);
            return null;
        }
    }
}
