package app.yolobolo.zeepkist.apps.playlistvoting.model;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private String token;
    private String name;
}
