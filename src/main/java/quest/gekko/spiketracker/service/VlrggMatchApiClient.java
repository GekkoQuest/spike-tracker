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
            final ResponseEntity<VlrggApiResponse> response = restTemplate.getForEntity(url, VlrggApiResponse.class);
            log.debug("Deserialized API Response: {}", response.getBody());
            return response.getBody();
        });

        return apiResponse != null ? apiResponse.getData() : null;
    }

    private <T> T executeWithRetry(final Supplier<T> apiCall) {
        int retryCount = 0;

        while (retryCount < MAX_RETRIES) {
            try {
                return apiCall.get();
            } catch (RestClientException e) {
                retryCount++;
                log.error("API call failed, retry {} of {}", retryCount, MAX_RETRIES, e);
                if (retryCount == MAX_RETRIES) {
                    log.error("Max retries reached, giving up.");
                    return null;
                }
            }
        }
        return null;
    }
}