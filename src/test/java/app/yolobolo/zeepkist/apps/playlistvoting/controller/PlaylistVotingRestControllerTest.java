package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.dto.request.CreateSessionRequest;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.Platform;
import app.yolobolo.zeepkist.apps.playlistvoting.model.enums.VoteOption;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.model.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                        .param("timer", "7:00"))
                .andExpect(status().isNoContent());

        verify(voteService).updateLobbyTimer("SESSION_TOKEN", "7:00");
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
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNoContent());

        verify(voteService).updatePlaylist(eq("SESSION_TOKEN"), any());
    }
}
