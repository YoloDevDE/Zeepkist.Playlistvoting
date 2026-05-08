package app.yolobolo.zeepkist.common.controller;

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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AccountControllerTest
{

    private MockMvc mockMvc;

    @Autowired
    private AccountController accountController;

    @MockitoBean
    private VoteService voteService;

    private SteamUserPrincipal testPrincipal;

    @BeforeEach
    void setup()
    {
        User testUser = new User();
        testUser.setId("user-id");
        testUser.setDisplayName("Test User");
        testUser.setToken("TOKEN");
        testUser.setRoles(Collections.emptyList());

        testPrincipal = SteamUserPrincipal.fromUser(testUser);

        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
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
    void profile_NoPrincipal_Redirects() throws Exception
    {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void profile_Success() throws Exception
    {
        User user = new User();
        user.setId("user-id");
        when(voteService.findUserById("user-id")).thenReturn(user);

        mockMvc.perform(get("/profile")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(view().name("common/profile"))
                .andExpect(model().attribute("host", user));
    }

    @Test
    void addManager_Success() throws Exception
    {
        User user = new User();
        user.setId("user-id");
        user.setManagerIds(new java.util.ArrayList<>());
        when(voteService.findUserById("user-id")).thenReturn(user);

        mockMvc.perform(post("/profile/manager/add")
                        .requestAttr("useTestUser", true)
                        .param("steamId", "manager-steam-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        verify(voteService).saveUser(user);
    }

    @Test
    void removeManager_Success() throws Exception
    {
        User user = new User();
        user.setId("user-id");
        user.setManagerIds(new java.util.ArrayList<>(Collections.singletonList("manager-steam-id")));
        when(voteService.findUserById("user-id")).thenReturn(user);

        mockMvc.perform(post("/profile/manager/remove")
                        .requestAttr("useTestUser", true)
                        .param("steamId", "manager-steam-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        verify(voteService).saveUser(user);
    }

    @Test
    void refreshToken_Success() throws Exception
    {
        User user = new User();
        user.setId("user-id");
        when(voteService.findUserById("user-id")).thenReturn(user);

        mockMvc.perform(post("/profile/token/refresh")
                        .requestAttr("useTestUser", true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        verify(voteService).saveUser(user);
    }

    @Test
    void deleteProfile_Success() throws Exception
    {
        mockMvc.perform(post("/profile/delete")
                        .requestAttr("useTestUser", true))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?deleted=true"));

        verify(voteService).deleteUser("user-id");
    }

    @Test
    void profile_UserNotFoundInDb_StillReturnsOk() throws Exception
    {
        when(voteService.findUserById("user-id")).thenReturn(null);

        mockMvc.perform(get("/profile")
                        .requestAttr("useTestUser", true))
                .andExpect(status().isOk())
                .andExpect(view().name("common/profile"))
                .andExpect(model().attribute("host", (Object) null))
                .andExpect(model().attribute("sessionCount", 0L))
                .andExpect(model().attribute("totalVotes", 0L))
                .andExpect(model().attribute("voteStats", java.util.Map.of()));
    }
}
