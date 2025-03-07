package quest.gekko.spiketracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpikeTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpikeTrackerApplication.class, args);
    }

}
