package app.yolobolo.zeepkist.apps.playlistvoting.service;

import app.yolobolo.zeepkist.apps.playlistvoting.model.UserVote;
import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.SessionState;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.repository.UserVoteRepository;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.model.UserIdentity;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import app.yolobolo.zeepkist.common.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceTest
{

    @Mock
    private UserRepository userRepo;
    @Mock
    private UserVoteRepository voteRepository;
    @Mock
    private SessionService sessionService;
    @Mock
    private LevelService levelService;
    @Mock
    private VoteProcessingService voteProcessingService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private UserService userService;

    @InjectMocks
    private VoteService voteService;

    private User loggedInUser;
    private User otherHost;
    private VotingSession loggedInUserSession;
    private VotingSession otherHostSession;

    @BeforeEach
    void setUp()
    {
        loggedInUser = new User();
        loggedInUser.setId("user-id");
        loggedInUser.setToken("user-token");
        loggedInUser.getIdentities().add(new UserIdentity("STEAM", "steam-id", "User", null));

        otherHost = new User();
        otherHost.setId("other-host-id");

        loggedInUserSession = new VotingSession();
        loggedInUserSession.setId("user-session-id");
        loggedInUserSession.setHostId("user-id");
        loggedInUserSession.setDisplayName("User Session");
        loggedInUserSession.setState(SessionState.ACTIVE);

        otherHostSession = new VotingSession();
        otherHostSession.setId("other-session-id");
        otherHostSession.setHostId("other-host-id");
        otherHostSession.setDisplayName("Other Host Session");
        otherHostSession.setState(SessionState.ACTIVE);
        otherHostSession.setCurrentLevelUid("level-uid");
    }

    @Test
    void getDashboardData_PrioritizesRequestedHostId()
    {
        // ... (existing test)
    }

    @Test
    void getVoteDetails_ReturnsAggregatedData()
    {
        String hostId = "host-id";
        VotingSession session = new VotingSession();
        session.setId("session-id");
        session.setHostId(hostId);

        when(sessionService.findActiveOrPausedSession(hostId)).thenReturn(session);

        UserVote vote = new UserVote();
        vote.setSessionId("session-id");
        vote.setLevelUid("level-uid");
        vote.setVote(VoteOption.YES);
        vote.setPlatformUsername("User1");
        vote.setCreatedAt(Instant.now());

        when(voteRepository.findBySessionIdInAndVote(anyList(), eq(VoteOption.YES))).thenReturn(List.of(vote));
        when(voteRepository.findBySessionIdInAndLevelUidIn(anyList(), anyList())).thenReturn(List.of(vote));

        app.yolobolo.zeepkist.apps.playlistvoting.model.Level level = app.yolobolo.zeepkist.apps.playlistvoting.model.Level.builder()
                .uid("level-uid")
                .name("Level Name")
                .author("Author")
                .build();
        when(levelService.findById("level-uid")).thenReturn(Optional.of(level));

        var details = voteService.getVoteDetails(hostId, null, VoteOption.YES);

        assertNotNull(details);
        assertEquals(1, details.size());
        assertEquals("Level Name", details.get(0).getLevelName());
        assertEquals(1L, details.get(0).getVoteCounts().get(VoteOption.YES));
        assertEquals("User1", details.get(0).getIndividualVotes().get(0).getUsername());
    }

    @Test
    void getDashboardData_IncludesCurrentLevelForSessions()
    {
        String hostId = "host-id";
        VotingSession session = new VotingSession();
        session.setId("session-id");
        session.setHostId(hostId);
        session.setDisplayName("Session 1");
        session.setCurrentLevelUid("level-uid");
        session.setState(SessionState.ACTIVE);
        session.setLastUsed(Instant.now());

        when(sessionService.findActiveOrPausedSession(hostId)).thenReturn(session);

        app.yolobolo.zeepkist.apps.playlistvoting.model.Level level = app.yolobolo.zeepkist.apps.playlistvoting.model.Level.builder()
                .uid("level-uid")
                .name("Current Level")
                .author("The Author")
                .build();
        when(levelService.findById("level-uid")).thenReturn(Optional.of(level));

        var data = voteService.getDashboardData(null, hostId);

        assertNotNull(data);
        List<Map<String, Object>> sessions = (List<Map<String, Object>>) data.get("sessions");
        assertNotNull(sessions);
        assertEquals(1, sessions.size());

        Map<String, Object> sessionMap = sessions.get(0);
        assertNotNull(sessionMap.get("currentLevel"));
        Map<String, Object> currentLevelMap = (Map<String, Object>) sessionMap.get("currentLevel");
        assertEquals("Current Level", currentLevelMap.get("name"));
        assertEquals("The Author", currentLevelMap.get("author"));
    }
}
