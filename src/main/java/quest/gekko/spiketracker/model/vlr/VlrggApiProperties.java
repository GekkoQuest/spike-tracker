package quest.gekko.spiketracker.model.vlr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "vlrgg.api")
public class VlrggApiProperties {
    private String baseUrl;
}
