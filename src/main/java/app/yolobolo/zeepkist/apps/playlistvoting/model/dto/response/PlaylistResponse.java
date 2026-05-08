package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import lombok.Data;

import java.util.List;

@Data
public class PlaylistResponse
{

    private String sessionName;
    private List<LevelWithVotes> levels;

    @Data
    public static class LevelWithVotes
    {
        private String uid;
        private String name;
        private String author;
        private List<UserVote> votes;
    }
}
