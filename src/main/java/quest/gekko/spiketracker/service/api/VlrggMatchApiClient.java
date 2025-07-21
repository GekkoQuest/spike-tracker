package quest.gekko.spiketracker.service.api;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import quest.gekko.spiketracker.config.ApplicationProperties;
import quest.gekko.spiketracker.model.api.VlrggApiResponse;
import quest.gekko.spiketracker.model.match.LiveMatchData;
import quest.gekko.spiketracker.model.match.MatchSegment;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class VlrggMatchApiClient {
    private final RestTemplate restTemplate;
    private final ApplicationProperties.VlrggApi properties;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    private final int maxConsecutiveFailures;
    private final long circuitBreakerTimeoutMs;
    private final int connectionTimeoutMs;
    private final int readTimeoutMs;

    private final MeterRegistry meterRegistry;
    private final Timer apiResponseTimer;

    private LocalDateTime lastSuccessfulCall = LocalDateTime.now();
    private String lastErrorMessage = null;

    public VlrggMatchApiClient(
            final RestTemplateBuilder restTemplateBuilder,
            final ApplicationProperties.VlrggApi properties,
            final MeterRegistry meterRegistry,
            @Value("${app.api.max-consecutive-failures:5}") final int maxConsecutiveFailures,
            @Value("${app.api.circuit-breaker-timeout-ms:60000}") final long circuitBreakerTimeoutMs,
            @Value("${app.api.connection-timeout-ms:10000}") final int connectionTimeoutMs,
            @Value("${app.api.read-timeout-ms:15000}") final int readTimeoutMs) {

        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.circuitBreakerTimeoutMs = circuitBreakerTimeoutMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;

        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectionTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();

        this.apiResponseTimer = Timer.builder("vlrgg.api.response.time")
                .description("VLR.gg API response time")
                .register(meterRegistry);
    }

    @PostConstruct
    public void validateConfiguration() {
        if (properties.baseUrl() == null || properties.baseUrl().trim().isEmpty()) {
            throw new IllegalStateException("VLR.gg API base URL must be configured");
        }

        try {
            URI.create(properties.baseUrl());
        } catch (final IllegalArgumentException e) {
            throw new IllegalStateException("Invalid VLR.gg API base URL: " + properties.baseUrl(), e);
        }

        log.info("VLR.gg API client configured with base URL: {}", properties.baseUrl());
    }

    public LiveMatchData getLiveMatchData() {
        if (isCircuitBreakerOpen()) {
            log.warn("Circuit breaker is open, skipping API call");
            return null;
        }

        final Timer.Sample sample = Timer.start();

        try {
            meterRegistry.counter("vlrgg.api.calls").increment();

            final String url = properties.baseUrl() + "/match?q=live_score";
            log.debug("Making API call to: {}", url);

            final ResponseEntity<VlrggApiResponse> response = restTemplate.getForEntity(url, VlrggApiResponse.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new HttpServerErrorException(response.getStatusCode(),
                        "API returned non-successful status: " + response.getStatusCode());
            }

            final VlrggApiResponse apiResponse = response.getBody();
            final LiveMatchData data = validateAndExtractData(apiResponse);

            // Reset circuit breaker on success
            onApiCallSuccess();

            log.debug("Successfully retrieved {} match segments", data != null && data.segments() != null ? data.segments().size() : 0);
            return data;
        } catch (final HttpClientErrorException e) {
            handleClientError(e);
            return null;
        } catch (final HttpServerErrorException e) {
            handleServerError(e);
            return null;
        } catch (final ResourceAccessException e) {
            handleNetworkError(e);
            return null;
        } catch (final Exception e) {
            handleGenericError(e);
            return null;
        } finally {
            sample.stop(apiResponseTimer);
        }
    }

    private LiveMatchData validateAndExtractData(final VlrggApiResponse apiResponse) {
        if (apiResponse == null) {
            log.warn("Received null API response");
            return new LiveMatchData(404, List.of());
        }

        final LiveMatchData data = apiResponse.data();

        if (data == null) {
            log.warn("API response contains null data field");
            return new LiveMatchData(500, List.of());
        }

        if (data.segments() != null) {
            final List<MatchSegment> validSegments = data.segments().stream()
                    .filter(this::isValidMatchSegment)
                    .toList();

            if (validSegments.size() != data.segments().size()) {
                log.warn("Filtered out {} invalid match segments", data.segments().size() - validSegments.size());
            }

            return new LiveMatchData(data.status(), validSegments);
        }

        return data;
    }

    private boolean isValidMatchSegment(final MatchSegment segment) {
        if (segment == null) {
            return false;
        }

        if (segment.match_page() == null || segment.match_page().trim().isEmpty()) {
            log.debug("Invalid segment: missing match_page");
            return false;
        }

        if (segment.team1() == null || segment.team1().trim().isEmpty() || segment.team2() == null || segment.team2().trim().isEmpty()) {
            log.debug("Invalid segment: missing team names");
            return false;
        }

        try {
            URI.create(segment.match_page());
        } catch (final IllegalArgumentException e) {
            log.debug("Invalid segment: malformed match_page URL: {}", segment.match_page());
            return false;
        }

        return true;
    }

    private boolean isCircuitBreakerOpen() {
        if (consecutiveFailures.get() < maxConsecutiveFailures) {
            return false;
        }

        final long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime.get();
        final boolean shouldRetry = timeSinceLastFailure > circuitBreakerTimeoutMs;

        if (shouldRetry) {
            log.info("Circuit breaker timeout expired, attempting to reset");
            consecutiveFailures.set(maxConsecutiveFailures - 1); // Allow one retry
        }

        return !shouldRetry;
    }

    private void onApiCallSuccess() {
        consecutiveFailures.set(0);
        lastSuccessfulCall = LocalDateTime.now();
        lastErrorMessage = null;
        log.debug("API call successful, circuit breaker reset");
    }

    private void onApiCallFailure(final String errorMessage) {
        final int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        lastErrorMessage = errorMessage;

        meterRegistry.counter("vlrgg.api.errors", "type", getErrorType(errorMessage)).increment();

        if (failures >= maxConsecutiveFailures) {
            log.error("Circuit breaker opened after {} consecutive failures. Last error: {}", failures, errorMessage);
        } else {
            log.warn("API call failed ({}/{}): {}", failures, maxConsecutiveFailures, errorMessage);
        }
    }

    private String getErrorType(final String errorMessage) {
        if (errorMessage == null) {
            return "unknown";
        }

        final String lower = errorMessage.toLowerCase();

        if (lower.contains("timeout") || lower.contains("connect")) {
            return "network";
        }

        if (lower.contains("404")) {
            return "not_found";
        }

        if (lower.contains("500") || lower.contains("502") || lower.contains("503")) {
            return "server";
        }

        if (lower.contains("401") || lower.contains("403")) {
            return "auth";
        }

        return "other";
    }

    private void handleClientError(final HttpClientErrorException e) {
        final String message = String.format("Client error: %s - %s", e.getStatusCode(), e.getMessage());
        onApiCallFailure(message);

        if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
            log.warn("API endpoint not found - check VLR.gg API documentation");
        } else if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Rate limited by VLR.gg API");
        } else {
            log.error("Client error calling VLR.gg API: {}", message);
        }
    }

    private void handleServerError(final HttpServerErrorException e) {
        final String message = String.format("Server error: %s - %s", e.getStatusCode(), e.getMessage());
        onApiCallFailure(message);
        log.error("Server error from VLR.gg API: {}", message);
    }

    private void handleNetworkError(final ResourceAccessException e) {
        final String message = "Network error: " + e.getMessage();
        onApiCallFailure(message);
        log.error("Network error calling VLR.gg API: {}", message);
    }

    private void handleGenericError(final Exception e) {
        final String message = "Unexpected error: " + e.getMessage();
        onApiCallFailure(message);
        log.error("Unexpected error calling VLR.gg API: {}", message, e);
    }

    public boolean isHealthy() {
        return consecutiveFailures.get() < maxConsecutiveFailures;
    }

    public Map<String, Object> getHealthDetails() {
        return Map.of(
                "isHealthy", isHealthy(),
                "consecutiveFailures", consecutiveFailures.get(),
                "maxConsecutiveFailures", maxConsecutiveFailures,
                "lastSuccessfulCall", lastSuccessfulCall,
                "lastErrorMessage", lastErrorMessage != null ? lastErrorMessage : "none",
                "circuitBreakerOpen", isCircuitBreakerOpen()
        );
    }

    public void resetCircuitBreaker() {
        consecutiveFailures.set(0);
        lastErrorMessage = null;
        log.info("Circuit breaker manually reset");
    }

    public boolean testConnection() {
        try {
            String url = properties.baseUrl() + "/match?q=live_score";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Connection test failed: {}", e.getMessage());
            return false;
        }
    }
}