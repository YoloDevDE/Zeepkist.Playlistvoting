package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class VotesResponse
{
    private long yes;
    private long no;
    private long abstain;
    private long total;
    private Map<String, Long> platforms;
}
