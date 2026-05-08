package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request;

import lombok.Data;

@Data
public class VetoRequest
{
    private String uid;
    private String veto; // "YES" or "NO"
}
