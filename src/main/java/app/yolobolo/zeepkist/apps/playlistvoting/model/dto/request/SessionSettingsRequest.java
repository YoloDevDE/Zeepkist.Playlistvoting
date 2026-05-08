package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VotingMode;
import lombok.Data;

import java.util.List;

@Data
public class SessionSettingsRequest
{
    private String displayName;
    private List<String> playlist;
    private boolean playlistMode;
    private SessionState state;
    private VotingMode votingMode;
}
