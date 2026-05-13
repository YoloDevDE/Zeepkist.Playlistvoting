package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.SessionSettingsRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.UpdatePlaylistRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.PlaylistResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.response.VotingResultResponse;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.*;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.model.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class PlaylistVotingRestControllerTest
{

    private MockMvc mockMvc;

    @Autowired
    private PlaylistVotingRestController playlistVotingRestController;

    @MockitoBean
    private VoteService voteService;

    private SteamUserPrincipal testPrincipal;

    @BeforeEach
    void setup()
    {
        User testUser = new User();
        testUser.setId("user-id");
        testUser.setDisplayName("Test User");
        testUser.setToken("SESSION_TOKEN");
        testUser.setRoles(java.util.List.of());
        testUser.setIdentities(java.util.List.of(new UserIdentity("STEAM", "76561198000000001", "Test User", null)));

        testPrincipal = SteamUserPrincipal.fromUser(testUser);

        mockMvc = MockMvcBuilders.standaloneSetup(playlistVotingRestController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver()
                {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter)
                    {
                        return parameter.hasParameterAnnotation(org.springframework.security.core.annotation.AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
                    {
                        return (webRequest.getHeader("Authorization") != null || webRequest.getAttribute("useTestUser", NativeWebRequest.SCOPE_REQUEST) != null) ? testPrincipal : null;
                    }
                })
                .build();
    }

    @Test
    void vote_NoToken_Unauthorized() throws Exception
    {
        mockMvc.perform(get("/api/playlistvoting/votes")
                        .param("platformUserId", "123")
                        .param("username", "testuser")
                        .param("platform", "TWITCH")
                        .param("vote", "YES"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void vote_SessionToken_Success() throws Exception
    {
        when(voteService.vote(eq("SESSION_TOKEN"), anyString(), anyString(), any(Platform.class), any(VoteOption.class)))
                .thenReturn("Vote recorded");

        mockMvc.perform(get("/api/playlistvoting/votes")
                        .requestAttr("useTestUser", true)
                        .param("platformUserId", "123")
                        .param("username", "testuser")
                        .param("platform", "TWITCH")
                        .param("vote", "YES"))
                .andExpect(status().isOk())
                .andExpect(content().string("Vote recorded"));
    }

    @Test
    void vote_QueryToken_Success() throws Exception
    {
        when(voteService.vote(eq("QUERY_TOKEN"), anyString(), anyString(), any(Platform.class), any(VoteOption.class)))
                .thenReturn("Vote recorded");

        mockMvc.perform(get("/api/playlistvoting/votes")
                        .param("token", "QUERY_TOKEN")
                        .param("platformUserId", "123")
                        .param("username", "testuser")
                        .param("platform", "TWITCH")
                        .param("vote", "YES"))
                .andExpect(status().isOk())
                .andExpect(content().string("Vote recorded"));
    }

    @Test
    void liveDashboard_NoSession_Success() throws Exception
    {
        when(voteService.getDashboardData(isNull(), isNull())).thenReturn(java.util.Map.of("sessions", java.util.Collections.emptyList()));

        mockMvc.perform(get("/api/playlistvoting/dashboard"))
                .andExpect(status().isOk());
    }

    @Test
    void updateLobbyTimer_Success() throws Exception
    {
        mockMvc.perform(post("/api/playlistvoting/sessions/active/timer")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundTime\": 300.0, \"levelLoadedAtTime\": 1000.0, \"currentTime\": 1100.0, \"gameState\": 0}"))
                .andExpect(status().isNoContent());

        verify(voteService).updateLobbyTimer(eq("SESSION_TOKEN"), any());
    }

    @Test
    void createSession_Success() throws Exception
    {
        app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession session = new app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession();
        session.setDisplayName("New Session");
        when(voteService.createSession(eq("SESSION_TOKEN"), any(CreateSessionRequest.class))).thenReturn(session);

        mockMvc.perform(post("/api/playlistvoting/sessions")
                        .requestAttr("useTestUser", true)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"New Session\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("Session created: New Session"));
    }

    @Test
    void createSession_Unauthorized() throws Exception
    {
        mockMvc.perform(post("/api/playlistvoting/sessions")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"New Session\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void downloadPlaylist_Success() throws Exception
    {
        when(voteService.downloadPlaylist(eq("session-id"), anyDouble(), anyBoolean(), isNull(), eq("user-id"), anyString()))
                .thenReturn("{}".getBytes());
        when(voteService.getPlaylistFilename(eq("session-id"), isNull())).thenReturn("playlist.zeeplist");

        mockMvc.perform(get("/api/playlistvoting/sessions/session-id/playlist")
                        .requestAttr("useTestUser", true)
                        .param("type", "tovote"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM));

        mockMvc.perform(get("/api/playlistvoting/sessions/session-id/playlist")
                        .requestAttr("useTestUser", true)
                        .param("type", "yes"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/playlistvoting/sessions/session-id/playlist")
                        .requestAttr("useTestUser", true)
                        .param("type", "no"))
                .andExpect(status().isOk());
    }

    @Test
    void updateActivePlaylist_Zeeplist_Success() throws Exception
    {
        String zeeplistJson = """
                {
                    "name": "S6_LEVELS",
                    "levels": [
                        {
                            "UID": "uid1",
                            "Name": "Level 1",
                            "Author": "Author 1",
                            "played": true
                        }
                    ]
                }
                """;
        String requestJson = "{\"playlistMode\": true, \"zeeplist\": " + zeeplistJson + "}";

        mockMvc.perform(post("/api/playlistvoting/sessions/active/playlist")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNoContent());

        verify(voteService).updatePlaylist(eq("SESSION_TOKEN"), any());
    }

    @Test
    void getToken_Success() throws Exception
    {
        mockMvc.perform(get("/api/playlistvoting/token")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(content().string("SESSION_TOKEN"));
    }

    @Test
    void updateSession_Success() throws Exception
    {
        mockMvc.perform(patch("/api/playlistvoting/sessions/session-id")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Updated Name\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).renameSession(eq("session-id"), eq("Updated Name"), eq("user-id"));
    }

    @Test
    void updateSessionState_Success() throws Exception
    {
        mockMvc.perform(patch("/api/playlistvoting/sessions/session-id/state")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"ACTIVE\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).updateSessionState(eq("session-id"), eq(SessionState.ACTIVE), eq("user-id"));
    }

    @Test
    void updateSessionSettings_Success() throws Exception
    {
        mockMvc.perform(put("/api/playlistvoting/sessions/session-id/settings")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Updated Settings\", \"playlistMode\": true, \"allowAbstain\": false, \"state\": \"ACTIVE\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).updateSessionSettings(eq("session-id"), eq("user-id"), any(SessionSettingsRequest.class));
    }

    @Test
    void resetSessionVotes_Success() throws Exception
    {
        mockMvc.perform(delete("/api/playlistvoting/sessions/session-id/votes")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isNoContent());

        verify(voteService).resetVotes(eq("session-id"), eq("user-id"));
    }

    @Test
    void deleteVote_Success() throws Exception
    {
        mockMvc.perform(delete("/api/playlistvoting/votes/vote-id")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isNoContent());

        verify(voteService).deleteVote(eq("vote-id"), eq("user-id"));
    }

    @Test
    void deleteSession_Success() throws Exception
    {
        mockMvc.perform(delete("/api/playlistvoting/sessions/session-id")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isNoContent());

        verify(voteService).deleteSession(eq("session-id"), eq("user-id"));
    }

    @Test
    void renameActiveSession_Success() throws Exception
    {
        app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession session = new app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession();
        session.setDisplayName("New Name");
        when(voteService.renameActiveSession(eq("SESSION_TOKEN"), anyString()))
                .thenReturn(session);

        mockMvc.perform(patch("/api/playlistvoting/sessions/active")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Session renamed to: New Name"));
    }

    @Test
    void pauseActiveSession_Success() throws Exception
    {
        app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession session = new app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession();
        session.setDisplayName("Paused Session");
        when(voteService.pauseSession("SESSION_TOKEN")).thenReturn(session);

        mockMvc.perform(post("/api/playlistvoting/sessions/active/pause")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(content().string("Session paused: Paused Session"));
    }

    @Test
    void resumeActiveSession_Success() throws Exception
    {
        app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession session = new app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession();
        session.setDisplayName("Resumed Session");
        when(voteService.resumeSession("SESSION_TOKEN")).thenReturn(session);

        mockMvc.perform(post("/api/playlistvoting/sessions/active/resume")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(content().string("Session resumed: Resumed Session"));
    }

    @Test
    void updateSessionPlaylist_Success() throws Exception
    {
        mockMvc.perform(post("/api/playlistvoting/sessions/session-id/playlist")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playlistMode\": true}"))
                .andExpect(status().isNoContent());

        verify(voteService).updateSessionPlaylist(eq("session-id"), eq("user-id"), any(UpdatePlaylistRequest.class));
    }

    @Test
    void vetoActiveLevel_Success() throws Exception
    {
        mockMvc.perform(post("/api/playlistvoting/sessions/active/veto")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\": \"level-uid\", \"veto\": \"YES\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).vetoLevel(eq("SESSION_TOKEN"), eq("level-uid"), eq(VetoValue.YES));
    }

    @Test
    void vetoLevel_Success() throws Exception
    {
        mockMvc.perform(post("/api/playlistvoting/sessions/session-id/levels/veto")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\": \"level-uid\", \"veto\": \"YES\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).vetoLevel(eq("session-id"), eq("level-uid"), eq(VetoValue.YES), eq("user-id"));
    }

    @Test
    void updateLevelStatus_Success() throws Exception
    {
        mockMvc.perform(patch("/api/playlistvoting/sessions/session-id/levels/status")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\": \"level-uid\", \"status\": \"VOTING_FINISHED\"}"))
                .andExpect(status().isNoContent());

        verify(voteService).updateLevelStatus(eq("session-id"), eq("level-uid"), eq(LevelStatus.VOTING_FINISHED), eq("user-id"));
    }

    @Test
    void resetActiveSessionVotes_Success() throws Exception
    {
        when(voteService.reset("SESSION_TOKEN")).thenReturn("Votes reset");

        mockMvc.perform(delete("/api/playlistvoting/sessions/active/votes")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(content().string("Votes reset"));
    }

    @Test
    void getActiveSessionResult_Success() throws Exception
    {
        VotingResultResponse response = VotingResultResponse.builder().build();
        when(voteService.getResult("SESSION_TOKEN")).thenReturn(response);

        mockMvc.perform(get("/api/playlistvoting/sessions/active/result")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk());
    }

    @Test
    void getActiveSessionLevel_Success() throws Exception
    {
        app.yolobolo.zeepkist.apps.playlistvoting.model.Level level = app.yolobolo.zeepkist.apps.playlistvoting.model.Level.builder()
                .name("Level Name")
                .build();
        when(voteService.getCurrentLevel("SESSION_TOKEN")).thenReturn(level);

        mockMvc.perform(get("/api/playlistvoting/sessions/active/level")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(content().string(level.toString()));
    }

    @Test
    void getActiveSessionPlaylist_Success() throws Exception
    {
        PlaylistResponse response = new PlaylistResponse();
        when(voteService.getPlaylist("SESSION_TOKEN")).thenReturn(response);

        mockMvc.perform(get("/api/playlistvoting/sessions/active/playlist")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk());
    }

    @Test
    void getDashboardData_Success() throws Exception
    {
        when(voteService.getDashboardData(eq("SESSION_TOKEN"), eq("host-id"))).thenReturn(Map.of());

        mockMvc.perform(get("/api/playlistvoting/dashboard")
                        .requestAttr("useTestUser", true)
                        .param("hostId", "host-id"))
                .andExpect(status().isOk());
    }

    @Test
    void castDashboardVote_Success() throws Exception
    {
        when(voteService.voteByHostId(eq("host-id"), eq("76561198000000001"), eq("Test User"), any(Platform.class), eq(VoteOption.YES)))
                .thenReturn("Vote cast");

        mockMvc.perform(post("/api/playlistvoting/dashboard/votes")
                        .requestAttr("useTestUser", true)
                        .param("hostId", "host-id")
                        .param("vote", "YES"))
                .andExpect(status().isOk())
                .andExpect(content().string("Vote cast"));
    }

    @Test
    void setCurrentLevel_Success() throws Exception
    {
        when(voteService.findActiveSession("SESSION_TOKEN")).thenReturn(new app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession());
        when(voteService.setCurrentLevel(eq("SESSION_TOKEN"), anyString(), anyString(), anyString(), any()))
                .thenReturn("Level set");

        mockMvc.perform(post("/api/playlistvoting/sessions/active/level")
                        .requestAttr("useTestUser", true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\": \"level-uid\", \"name\": \"Level Name\", \"author\": \"Author\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("Level set"));
    }
}
