package app.yolobolo.zeepkist.playlistvoting.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document("zk_sessions")
public class ZkSession {
    @Id
    private String id;
    private String hostId;
    private String displayName;
    private String currentLevelUid;
    private List<String> playedLevels;
    private SessionState state;
    private Instant createdAt;
    private Instant lastUsed;

    public ZkSession() {
        this.playedLevels = new ArrayList<>();
        this.state = SessionState.ACTIVE;
        this.createdAt = Instant.now();
        this.lastUsed = Instant.now();
    }
}
