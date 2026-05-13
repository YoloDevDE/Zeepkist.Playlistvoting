package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VetoValue;
import lombok.Data;

@Data
public class VetoRequest
{
    private String uid;
    private VetoValue veto;
}
