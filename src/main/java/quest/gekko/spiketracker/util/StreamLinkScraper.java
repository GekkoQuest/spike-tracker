package quest.gekko.spiketracker.util;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;

@Slf4j
public final class StreamLinkScraper {

    private StreamLinkScraper() {
        throw new UnsupportedOperationException("Utility class should not be instantiated.");
    }

    public static String scrapeStreamLink(final String matchUrl) {
        try {
            final Document document = Jsoup.connect(matchUrl).get();
            final Element streamLinkElement = document.selectFirst("div.match-streams-container a[href]");

            if (streamLinkElement != null) {
                final String streamLink = streamLinkElement.attr("href");
                log.info("Scraped stream link: {}", streamLink);
                return streamLink;
            } else {
                log.warn("Stream link HTML element NOT found");
            }
        } catch (final IOException e) {
            log.error("Jsoup scrape failed for {}", matchUrl, e);
        }

        return null;
    }

}