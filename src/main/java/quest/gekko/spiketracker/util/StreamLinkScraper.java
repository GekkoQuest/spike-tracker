package quest.gekko.spiketracker.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StreamLinkScraper {
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private static final Set<String> ALLOWED_DOMAINS = Set.of(
            "www.vlr.gg", "vlr.gg"
    );

    private static final Set<String> VALID_STREAM_DOMAINS = Set.of(
            "twitch.tv", "www.twitch.tv",
            "youtube.com", "www.youtube.com", "youtu.be",
            "facebook.com", "www.facebook.com",
            "kick.com", "www.kick.com"
    );

    private static final Pattern URL_VALIDATION_PATTERN = Pattern.compile(
            "^https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"
    );

    @Value("${app.scraping.enabled:true}")
    private boolean scrapingEnabled;

    @Value("${app.scraping.user-agent:Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36}")
    private String userAgent;

    @Cacheable(value = "streamLinks", key = "#matchUrl")
    public String scrapeStreamLink(final String matchUrl) {
        if (!scrapingEnabled) {
            log.debug("Stream link scraping is disabled");
            return null;
        }

        if (!isValidAndAllowedUrl(matchUrl)) {
            log.warn("Invalid or disallowed URL for scraping: {}", matchUrl);
            return null;
        }

        return scrapeStreamLinkWithRetry(matchUrl, 0);
    }

    private boolean isValidAndAllowedUrl(final String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        if (!URL_VALIDATION_PATTERN.matcher(url).matches()) {
            return false;
        }

        try {
            final URI uri = new URI(url);
            final String host = uri.getHost();

            if (host == null) {
                return false;
            }

            return ALLOWED_DOMAINS.contains(host.toLowerCase());
        } catch (final URISyntaxException e) {
            log.warn("Invalid URL syntax: {}", url);
            return false;
        }
    }

    private String scrapeStreamLinkWithRetry(final String matchUrl, final int attempt) {
        try {
            log.debug("Attempting to scrape stream link from: {} (attempt {})", matchUrl, attempt + 1);

            final Document document = Jsoup.connect(matchUrl)
                    .timeout(TIMEOUT_MS)
                    .userAgent(userAgent)
                    .followRedirects(true)
                    .maxBodySize(1024 * 1024)
                    .ignoreContentType(false)
                    .ignoreHttpErrors(false)
                    .get();

            final Optional<String> streamLink = tryMultipleSelectors(document);

            if (streamLink.isPresent()) {
                final String link = streamLink.get();

                if (isValidStreamLink(link)) {
                    final String normalizedLink = normalizeStreamLink(link);
                    log.info("Successfully scraped stream link: {}", normalizedLink);
                    return normalizedLink;
                } else {
                    log.warn("Found invalid stream link: {}", link);
                    return null;
                }
            } else {
                log.warn("No stream link found in match page: {}", matchUrl);
                return null;
            }

        } catch (final IOException e) {
            log.warn("Failed to scrape stream link from {} (attempt {}): {}", matchUrl, attempt + 1, e.getMessage());

            if (attempt < MAX_RETRIES - 1) {
                try {
                    Thread.sleep(RETRY_DELAY.toMillis() * (attempt + 1));
                    return scrapeStreamLinkWithRetry(matchUrl, attempt + 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Thread interrupted during retry delay", ie);
                    return null;
                }
            } else {
                log.error("Failed to scrape stream link after {} attempts", MAX_RETRIES);
                return null;
            }
        } catch (Exception e) {
            log.error("Unexpected error while scraping stream link from {}: {}", matchUrl, e.getMessage(), e);
            return null;
        }
    }

    private Optional<String> tryMultipleSelectors(final Document document) {
        String[] selectors = {
                "div.match-streams-container a[href]",
                ".match-streams a[href*='twitch']",
                ".match-streams a[href*='youtube']",
                "a[href*='twitch.tv']",
                "a[href*='youtube.com']",
                "a[href*='youtu.be']",
                ".streams-container a",
                "[data-stream-link]",
                ".match-streams a"
        };

        for (String selector : selectors) {
            try {
                final Elements elements = document.select(selector);

                for (Element element : elements) {
                    final String href = element.attr("href");

                    if (isValidStreamLink(href)) {
                        return Optional.of(href);
                    }
                }
            } catch (final Exception e) {
                log.debug("Error with selector '{}': {}", selector, e.getMessage());
            }
        }

        return Optional.empty();
    }

    private boolean isValidStreamLink(String href) {
        if (href == null || href.trim().isEmpty()) {
            return false;
        }

        href = href.trim().toLowerCase();

        try {
            if (href.startsWith("//")) {
                href = "https:" + href;
            } else if (href.startsWith("/")) {
                return false; // Relative paths are not valid stream links
            }

            if (!href.startsWith("http://") && !href.startsWith("https://")) {
                return false;
            }

            final URI uri = new URI(href);
            final String host = uri.getHost();

            if (host == null) {
                return false;
            }

            return VALID_STREAM_DOMAINS.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        } catch (final URISyntaxException e) {
            return false;
        }
    }

    private String normalizeStreamLink(String link) {
        if (link == null) {
            return null;
        }

        link = link.trim();

        if (link.startsWith("http://")) {
            link = link.replace("http://", "https://");
        } else if (link.startsWith("//")) {
            link = "https:" + link;
        }

        return link;
    }

    public CompletableFuture<String> scrapeStreamLinkAsync(final String matchUrl) {
        return CompletableFuture.supplyAsync(() -> scrapeStreamLink(matchUrl))
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("Async stream link scraping failed for {}: {}", matchUrl, throwable.getMessage());
                    return null;
                });
    }

    public void prefetchStreamLinks(final String... matchUrls) {
        if (!scrapingEnabled) {
            return;
        }

        for (String url : matchUrls) {
            if (isValidAndAllowedUrl(url)) {
                CompletableFuture.runAsync(() -> scrapeStreamLink(url))
                        .exceptionally(throwable -> {
                            log.debug("Prefetch failed for {}: {}", url, throwable.getMessage());
                            return null;
                        });
            }
        }
    }

    public boolean isHealthy() {
        return scrapingEnabled;
    }

    public void setScrapingEnabled(boolean enabled) {
        this.scrapingEnabled = enabled;
        log.info("Stream link scraping {}", enabled ? "enabled" : "disabled");
    }
}