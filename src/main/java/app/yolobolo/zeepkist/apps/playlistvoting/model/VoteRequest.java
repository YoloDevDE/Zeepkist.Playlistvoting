package app.yolobolo.zeepkist.apps.playlistvoting.model;

import lombok.Data;

@Data
public class VoteRequest {
    private String token;
    private String platformUserId;
    private Platform platform;
    private VoteOption vote;
}
