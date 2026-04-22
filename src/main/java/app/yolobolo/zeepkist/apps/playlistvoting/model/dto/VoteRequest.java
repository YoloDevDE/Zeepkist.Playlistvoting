package app.yolobolo.zeepkist.apps.playlistvoting.model.dto;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import lombok.Data;

@Data
public class VoteRequest {
    private String token;
    private String platformUserId;
    private Platform platform;
    private VoteOption vote;
}
