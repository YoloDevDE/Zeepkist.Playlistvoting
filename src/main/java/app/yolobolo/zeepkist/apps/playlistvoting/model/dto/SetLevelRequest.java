package app.yolobolo.zeepkist.apps.playlistvoting.model.dto;

import lombok.Data;

@Data
public class SetLevelRequest {
    private String token;
    private String uid;
    private String name;
    private String author;
    private Long workshopID;
}
