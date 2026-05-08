package app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response;

import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoteDetailResponse
{
    private String levelUid;
    private String levelName;
    private String levelAuthor;
    private String workshopID;
    private Instant lastVotedAt;
    private Map<VoteOption, Long> voteCounts;
    private List<IndividualVoteDTO> individualVotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndividualVoteDTO
    {
        private String username;
        private VoteOption vote;
        private Instant date;
    }
}
