package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import lombok.Data;

@Data
public class SetLevelRequest
{
    private String uid;
    private String name;
    private String author;
    private Long workshopID;
}
