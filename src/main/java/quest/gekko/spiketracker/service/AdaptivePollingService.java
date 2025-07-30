package quest.gekko.spiketracker.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdaptivePollingService {
    private final int activeInterval;
    private final int idleInterval;
    private final int deepIdleInterval;
    private final int maxEmptyPolls;

    @Getter
    private int currentInterval;
    @Getter
    private int consecutiveEmptyPolls = 0;
    @Getter
    private String pollingMode = "ACTIVE";

    public AdaptivePollingService(
            @Value("${app.match-tracking.active-interval:15000}") final int activeInterval,
            @Value("${app.match-tracking.idle-interval:120000}") final int idleInterval,
            @Value("${app.match-tracking.deep-idle-interval:300000}") final int deepIdleInterval,
            @Value("${app.match-tracking.max-empty-polls:10}") final int maxEmptyPolls) {
        this.activeInterval = activeInterval;
        this.idleInterval = idleInterval;
        this.deepIdleInterval = deepIdleInterval;
        this.maxEmptyPolls = maxEmptyPolls;
        this.currentInterval = activeInterval;

        log.info("Adaptive polling configured - Active: {}ms, Idle: {}ms, Deep Idle: {}ms",
                activeInterval, idleInterval, deepIdleInterval);
    }

    public int getNextInterval(boolean hasLiveMatches) {
        if (hasLiveMatches) {
            consecutiveEmptyPolls = 0;
            currentInterval = activeInterval;
            pollingMode = "ACTIVE";
            return activeInterval;
        }

        consecutiveEmptyPolls++;

        if (consecutiveEmptyPolls > maxEmptyPolls) {
            currentInterval = deepIdleInterval;
            pollingMode = "DEEP_IDLE";
            log.debug("Switching to deep idle mode after {} empty polls", consecutiveEmptyPolls);
            return deepIdleInterval;
        } else if (consecutiveEmptyPolls > (maxEmptyPolls / 2)) {
            currentInterval = idleInterval;
            pollingMode = "IDLE";
            log.debug("Switching to idle mode after {} empty polls", consecutiveEmptyPolls);
            return idleInterval;
        }

        currentInterval = activeInterval;
        pollingMode = "ACTIVE";
        return activeInterval;
    }

    public double calculateHourlyDbConnections() {
        return 3600000.0 / currentInterval; // 3600000ms = 1 hour
    }

    public void reset() {
        consecutiveEmptyPolls = 0;
        currentInterval = activeInterval;
        pollingMode = "ACTIVE";
        log.info("Adaptive polling reset to active mode");
    }
}