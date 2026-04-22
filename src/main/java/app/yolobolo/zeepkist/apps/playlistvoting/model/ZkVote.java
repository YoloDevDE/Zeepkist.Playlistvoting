package app.yolobolo.zeepkist.apps.playlistvoting.model;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("zk_votes")
public class ZkVote {
    @Id
    private String id;
    private String sessionId;
    private String levelUid;
    private Platform platform;
    private String platformUserId;
    private String platformUsername;
    private VoteOption vote;
    private Instant createdAt;
    private Instant modifiedAt;

    public ZkVote() {
        this.createdAt = Instant.now();
        this.modifiedAt = Instant.now();
    }
}
