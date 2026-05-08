package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.ZeeplistDTO;
import lombok.Data;

@Data
public class CreateSessionRequest
{
    private String name;
    private boolean playlistMode;
    private double roundLength;
    private ZeeplistDTO zeeplist;
}
