package app.yolobolo.zeepkist.apps.playlistvoting.model;

import lombok.Data;

@Data
public class RenameSessionRequest {
    private String token;
    private String name;
}
