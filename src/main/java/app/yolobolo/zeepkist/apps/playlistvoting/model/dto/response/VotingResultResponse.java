package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.LobbyGameState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VetoValue;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VotingResultResponse
{
    private String sessionName;
    private String sessionState;
    private double roundTime;
    private double levelLoadedAtTime;
    private double currentTime;
    private LobbyGameState lobbyGameState;
    private String lobbyTimer;
    private LevelResponse level;
    private VotesResponse votes;
    private VetoValue veto;
}
