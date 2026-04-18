package app.yolobolo.zeepkist.playlistvoting.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Document("zk_hosts")
public class ZkHost {
    @Id
    private String id;
    @Indexed(unique = true)
    private String token;
    @Indexed(unique = true, sparse = true)
    private String steamId;
    private String steamName;
    private List<String> managerSteamIds = new ArrayList<>();
    private Instant lastUsed;

    public ZkHost() {
        this.token = UUID.randomUUID().toString().toUpperCase();
        this.lastUsed = Instant.now();
    }
}
