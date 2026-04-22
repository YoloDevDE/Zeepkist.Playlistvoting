package app.yolobolo.zeepkist.apps.playlistvoting.model.dto;

import lombok.Data;

@Data
public class CreateSessionRequest {
    private String token;
    private String name;
}
