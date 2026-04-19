package app.yolobolo.zeepkist.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@Configuration
public class MongoConfig {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${spring.mongodb.drop-on-startup:false}")
    private boolean dropOnStartup;

    @PostConstruct
    public void init() {
        if (dropOnStartup) {
            log.info("Dropping database as requested by spring.mongodb.drop-on-startup=true");
            mongoTemplate.getDb().drop();
        }
    }
}
