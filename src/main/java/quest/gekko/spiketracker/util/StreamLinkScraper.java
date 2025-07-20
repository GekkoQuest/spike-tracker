package quest.gekko.spiketracker.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class StreamLinkScraper {

    private static final int TIMEOUT_MS = 10000; // 10 seconds
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    @Cacheable(value = "streamLinks", key = "#matchUrl")
    public String scrapeStreamLink(final String matchUrl) {
        return scrapeStreamLinkWithRetry(matchUrl, 0);
    }

    private String scrapeStreamLinkWithRetry(final String matchUrl, int attempt) {
        try {
            log.debug("Attempting to scrape stream link from: {} (attempt {})", matchUrl, attempt + 1);

            final Document document = Jsoup.connect(matchUrl)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .followRedirects(true)
                    .get();

            // Try multiple selectors for stream links
            Optional<String> streamLink = tryMultipleSelectors(document);

            if (streamLink.isPresent()) {
                String link = streamLink.get();
                log.info("Successfully scraped stream link: {}", link);
                return link;
            } else {
                log.warn("No stream link found in match page: {}", matchUrl);
                return null;
            }

        } catch (final IOException e) {
            log.warn("Failed to scrape stream link from {} (attempt {}): {}",
                    matchUrl, attempt + 1, e.getMessage());

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
        }
    }

    private Optional<String> tryMultipleSelectors(final Document document) {
        // Try different CSS selectors to find stream links
        String[] selectors = {
                "div.match-streams-container a[href]",
                ".match-streams a[href*='twitch']",
                ".match-streams a[href*='youtube']",
                "a[href*='twitch.tv']",
                "a[href*='youtube.com']",
                ".streams-container a",
                "[data-stream-link]"
        };

        for (String selector : selectors) {
            Elements elements = document.select(selector);
            for (Element element : elements) {
                String href = element.attr("href");
                if (isValidStreamLink(href)) {
                    return Optional.of(href);
                }
            }
        }

        return Optional.empty();
    }

    private boolean isValidStreamLink(String href) {
        if (href == null || href.trim().isEmpty()) {
            return false;
        }

        href = href.toLowerCase();
        return href.contains("twitch.tv") ||
                href.contains("youtube.com") ||
                href.contains("youtu.be") ||
                href.contains("facebook.com") ||
                href.contains("stream") && (href.startsWith("http") || href.startsWith("//"));
    }

    public CompletableFuture<String> scrapeStreamLinkAsync(final String matchUrl) {
        return CompletableFuture.supplyAsync(() -> scrapeStreamLink(matchUrl))
                .orTimeout(15, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    log.error("Async stream link scraping failed for {}", matchUrl, throwable);
                    return null;
                });
    }

    public void prefetchStreamLinks(final String... matchUrls) {
        for (String url : matchUrls) {
            CompletableFuture.runAsync(() -> scrapeStreamLink(url));
        }
    }
}