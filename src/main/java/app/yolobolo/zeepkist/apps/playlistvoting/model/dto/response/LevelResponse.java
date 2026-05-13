package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;


import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.LevelStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LevelResponse
{
    private String levelUid;
    private String levelName;
    private String levelAuthor;
    private Long workshopId;
    private LevelStatus status;
}
