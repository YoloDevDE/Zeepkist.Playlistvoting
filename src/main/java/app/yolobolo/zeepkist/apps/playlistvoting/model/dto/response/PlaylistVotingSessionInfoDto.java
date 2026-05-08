package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlaylistVotingSessionInfoDto
{
    private String id;
    private String displayName;
    private SessionState state;
    private boolean playlistModeEnabled;
    private boolean hasPlaylist;
    private int playlistLevelCount;
    private String currentLevelUid;
    private int totalLevelCount;
    private int finalizedLevelCount;
    private int remainingLevelCount;
    private VotingResultResponse latestResult;
}
