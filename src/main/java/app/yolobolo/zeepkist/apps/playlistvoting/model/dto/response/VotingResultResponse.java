package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VotingResultResponse
{
    private String sessionName;
    private String sessionState;
    private String lobbyTimer;
    private LevelResponse level;
    private VotesResponse votes;
    private String veto;
}
