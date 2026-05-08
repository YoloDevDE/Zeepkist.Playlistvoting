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
    private boolean allowAbstain;
    private Map<String, Long> platforms;

    public int getYesPct()
    {
        if (total == 0)
        {
            return 0;
        }
        return (int) Math.round((double) yes / total * 100);
    }
}
