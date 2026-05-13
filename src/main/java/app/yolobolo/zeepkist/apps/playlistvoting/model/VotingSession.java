package app.yolobolo.zeepkist.apps.playlistvoting.model;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.*;
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
    private Map<String, LevelStatus> levelStatuses;
    private SessionState state;
    private Instant createdAt;
    private Instant lastUsed;
    private double roundTime;
    private double levelLoadedAtTime;
    private double currentTime;
    private LobbyGameState lobbyGameState;
    private String lobbyTimer;
    private List<String> playlist;
    private boolean playlistMode;
    private boolean allowAbstain;
    private Map<String, VetoValue> vetoes;
    private VotingMode votingMode;

    public VotingSession()
    {
        this.playedLevels = new ArrayList<>();
        this.levelStatuses = new HashMap<>();
        this.playlist = new ArrayList<>();
        this.playlistMode = false;
        this.allowAbstain = true;
        this.vetoes = new HashMap<>();
        this.votingMode = VotingMode.NORMAL;
        this.state = SessionState.ACTIVE;
        this.createdAt = Instant.now();
        this.lastUsed = Instant.now();
        this.lobbyGameState = LobbyGameState.GAME;
    }

    public boolean isConnected()
    {
        return lastUsed != null && lastUsed.isAfter(Instant.now().minusSeconds(30));
    }
}
