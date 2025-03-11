package quest.gekko.spiketracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import quest.gekko.spiketracker.model.vlr.VlrggApiResponse;
import quest.gekko.spiketracker.model.vlr.VlrggApiProperties;
import quest.gekko.spiketracker.model.match.LiveMatchData;

import java.util.function.Supplier;

@Slf4j
@Service
public class VlrggMatchApiClient {
    private final RestTemplate restTemplate;
    private final VlrggApiProperties apiProperties;

    private static final int MAX_RETRIES = 3;

    public VlrggMatchApiClient(final RestTemplate restTemplate, final VlrggApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiProperties = apiProperties;
    }

    public LiveMatchData getLiveMatchData() {
        final VlrggApiResponse apiResponse = executeWithRetry(() -> {
            final String url = apiProperties.getBaseUrl() + "/match?q=live_score";
            log.debug("Fetching live match data from: {}", url);
            final ResponseEntity<VlrggApiResponse> response = restTemplate.getForEntity(url, VlrggApiResponse.class);
            return response.getBody() !=  null ? response.getBody() : null;
        });

        return apiResponse != null ? apiResponse.getData() : null;
    }

    private <T> T executeWithRetry(final Supplier<T> apiCall) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return apiCall.get();
            } catch (final RestClientException e) {
                log.warn("Retry attempt #{}: {}", attempt, e.getMessage());
            }
        }

        log.error("API call failed after {} attempts.", MAX_RETRIES);
        return  null;
    }
}