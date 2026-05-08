package app.yolobolo.zeepkist.apps.playlistvoting.model;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Document
public class VotingSession
{
    @Id
    private String id;
    private String hostId;
    private String displayName;
    private String currentLevelUid;
    private List<String> playedLevels;
    private Map<String, String> levelStatuses;
    private SessionState state;
    private Instant createdAt;
    private Instant lastUsed;
    private String lobbyTimer;
    private List<String> playlist;
    private boolean playlistMode;
    private Map<String, String> vetoes; // uid -> "YES" / "NO"

    public VotingSession()
    {
        this.playedLevels = new ArrayList<>();
        this.levelStatuses = new HashMap<>();
        this.playlist = new ArrayList<>();
        this.playlistMode = false;
        this.vetoes = new HashMap<>();
        this.state = SessionState.ACTIVE;
        this.createdAt = Instant.now();
        this.lastUsed = Instant.now();
        this.lobbyTimer = "0:00";
    }
}
