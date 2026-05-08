package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotesResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.UserVoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VoteProcessingServiceTest
{

    @Mock
    private UserVoteRepository voteRepository;

    @InjectMocks
    private VoteProcessingService voteProcessingService;

    private List<UserVote> testVotes;

    @BeforeEach
    void setUp()
    {
        UserVote vote1 = new UserVote();
        vote1.setVote(VoteOption.YES);
        vote1.setPlatform(Platform.TWITCH);

        UserVote vote2 = new UserVote();
        vote2.setVote(VoteOption.NO);
        vote2.setPlatform(Platform.TWITCH);

        UserVote vote3 = new UserVote();
        vote3.setVote(VoteOption.ABSTAIN);
        vote3.setPlatform(Platform.DISCORD);

        UserVote vote4 = new UserVote();
        vote4.setVote(VoteOption.IDK);
        vote4.setPlatform(Platform.KIK);

        testVotes = Arrays.asList(vote1, vote2, vote3, vote4);
    }

    @Test
    void calculateVotesResponse_AllowAbstainTrue()
    {
        VotesResponse response = voteProcessingService.calculateVotesResponse(testVotes, true);

        assertEquals(1, response.getYes());
        assertEquals(1, response.getNo());
        assertEquals(1, response.getAbstain());
        assertEquals(3, response.getTotal());
        assertTrue(response.isAllowAbstain());
        assertEquals(3, response.getPlatforms().size());
        assertEquals(2L, response.getPlatforms().get("TWITCH"));
        assertEquals(1L, response.getPlatforms().get("DISCORD"));
        // IDK is not included in platforms if allowAbstain is true? 
        // Wait, line 116: filter(v -> allowAbstain || (v.getVote() != VoteOption.ABSTAIN && v.getVote() != VoteOption.IDK))
        // If allowAbstain is true, it passes everything.
        assertEquals(1L, response.getPlatforms().get("KIK"));
    }

    @Test
    void calculateVotesResponse_AllowAbstainFalse()
    {
        VotesResponse response = voteProcessingService.calculateVotesResponse(testVotes, false);

        assertEquals(1, response.getYes());
        assertEquals(1, response.getNo());
        assertEquals(0, response.getAbstain());
        assertEquals(2, response.getTotal());
        assertFalse(response.isAllowAbstain());
        assertEquals(1, response.getPlatforms().size());
        assertEquals(2L, response.getPlatforms().get("TWITCH"));
        assertNull(response.getPlatforms().get("DISCORD"));
        assertNull(response.getPlatforms().get("KIK"));
    }
}
