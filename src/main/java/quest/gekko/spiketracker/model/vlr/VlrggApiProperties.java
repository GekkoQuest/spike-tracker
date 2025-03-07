package quest.gekko.spiketracker.model.vlr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "vlrgg.api")
@Configuration
@Data
public class VlrggApiProperties {
    private String baseUrl;
}
