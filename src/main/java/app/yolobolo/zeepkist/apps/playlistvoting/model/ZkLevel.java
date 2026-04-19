package app.yolobolo.zeepkist.apps.playlistvoting.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@Document("zk_levels")
public class ZkLevel {
    @Id
    private String uid;
    private String name;
    private String author;
    private Long workshopID;
}
