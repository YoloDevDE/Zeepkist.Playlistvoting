package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import lombok.Data;

@Data
public class SessionStateRequest
{
    private SessionState state;
}
