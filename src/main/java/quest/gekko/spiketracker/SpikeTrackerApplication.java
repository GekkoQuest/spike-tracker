package quest.gekko.spiketracker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
public class SpikeTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeTrackerApplication.class, args);
        log.info("SpikeTrackerApplication started successfully!");
    }

}
