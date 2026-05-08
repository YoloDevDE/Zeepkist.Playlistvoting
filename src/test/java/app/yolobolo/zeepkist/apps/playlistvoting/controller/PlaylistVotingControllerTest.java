package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.model.VotingSession;
import app.yolobolo.zeepkist.apps.playlistvoting.service.LevelService;
import app.yolobolo.zeepkist.apps.playlistvoting.service.SessionService;
import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class PlaylistVotingControllerTest
{

    private MockMvc mockMvc;

    @Autowired
    private PlaylistVotingController playlistVotingController;

    @MockitoBean
    private VoteService voteService;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private LevelService levelService;

    private SteamUserPrincipal testPrincipal;

    @BeforeEach
    void setup()
    {
        User testUser = new User();
        testUser.setId("user-id");
        testUser.setDisplayName("Test User");
        testUser.setToken("TOKEN");
        testUser.setRoles(Collections.emptyList());
        testUser.setIdentities(List.of(new app.yolobolo.zeepkist.common.model.UserIdentity("STEAM", "76561198000000001", "Test User", null)));

        testPrincipal = SteamUserPrincipal.fromUser(testUser);

        mockMvc = MockMvcBuilders.standaloneSetup(playlistVotingController)
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
                        return webRequest.getAttribute("useTestUser", NativeWebRequest.SCOPE_REQUEST) != null ? testPrincipal : null;
                    }
                })
                .build();
    }

    @Test
    void list_Success() throws Exception
    {
        when(voteService.findAllHostsWithSessions()).thenReturn(List.of(Map.of("activeSession", new VotingSession())));

        mockMvc.perform(get("/playlistvoting"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps/playlistvoting/list"))
                .andExpect(model().attributeExists("hosts"))
                .andExpect(model().attribute("activeSessionsCount", 1L));
    }

    @Test
    void myDashboard_WithPrincipal_RedirectsToHost() throws Exception
    {
        mockMvc.perform(get("/playlistvoting/dashboard")
                        .requestAttr("useTestUser", true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/playlistvoting/76561198000000001")); // SteamId from SteamUserPrincipal defaults in some way? 
        // Ah, I didn't set steamId in setup.
    }

    @Test
    void hostDashboard_HostNotFound_Redirects() throws Exception
    {
        when(voteService.findUserBySteamId("invalid")).thenReturn(null);

        mockMvc.perform(get("/playlistvoting/invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/playlistvoting?error=host_not_found"));
    }

    @Test
    void hostDashboard_Success() throws Exception
    {
        User host = new User();
        host.setId("host-id");
        host.setToken("HOST_TOKEN");
        when(voteService.findUserBySteamId("steam-id")).thenReturn(host);
        when(voteService.findAllSessions("host-id")).thenReturn(Collections.emptyList());
        when(sessionService.findActiveOrPausedSession("host-id")).thenReturn(null);

        mockMvc.perform(get("/playlistvoting/steam-id"))
                .andExpect(status().isOk())
                .andExpect(view().name("apps/playlistvoting/dashboard"))
                .andExpect(model().attribute("host", host))
                .andExpect(model().attribute("isOwner", false));
    }
}
