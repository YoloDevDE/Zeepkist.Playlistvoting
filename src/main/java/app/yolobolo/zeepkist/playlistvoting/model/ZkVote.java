package app.yolobolo.zeepkist.playlistvoting.model;

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
    private VoteOption vote;
    private Instant createdAt;
    private Instant modifiedAt;

    public ZkVote() {
        this.createdAt = Instant.now();
        this.modifiedAt = Instant.now();
    }
}
