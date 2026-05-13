package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.LevelStatus;
import lombok.Data;

@Data
public class LevelStatusRequest
{
    private String uid;
    private LevelStatus status;
}
