package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.ZeeplistDTO;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePlaylistRequest
{
    private List<String> playlist;
    private boolean playlistMode;
    private ZeeplistDTO zeeplist;
}
