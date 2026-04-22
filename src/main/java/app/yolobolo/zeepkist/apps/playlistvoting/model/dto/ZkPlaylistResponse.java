package app.yolobolo.zeepkist.apps.playlistvoting.model.dto;

import app.yolobolo.zeepkist.apps.playlistvoting.model.ZkVote;
import lombok.Data;

import java.util.List;

@Data
public class ZkPlaylistResponse {

    private String sessionName;
    private List<LevelWithVotes> levels;

    @Data
    public static class LevelWithVotes {
        private String uid;
        private String name;
        private String author;
        private List<ZkVote> votes;
    }
}
