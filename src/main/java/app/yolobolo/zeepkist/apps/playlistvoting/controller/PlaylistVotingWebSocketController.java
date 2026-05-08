package app.yolobolo.zeepkist.apps.playlistvoting.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@Slf4j
@RequiredArgsConstructor
public class PlaylistVotingWebSocketController
{
    private final VoteService voteService;

    @MessageMapping("/timer")
    public void updateLobbyTimer(@Payload String timer, Principal principal)
    {
        if (!(principal instanceof UsernamePasswordAuthenticationToken auth))
        {
            log.warn("Lobby timer update failed - no authentication found in WebSocket session");
            return;
        }

        if (!(auth.getPrincipal() instanceof SteamUserPrincipal userPrincipal))
        {
            log.warn("Lobby timer update failed - principal is not a SteamUserPrincipal");
            return;
        }

        log.info("Lobby timer update via WebSocket for host: {} - timer: {}", userPrincipal.getHostId(), timer);
        voteService.updateLobbyTimer(userPrincipal.getToken(), timer);
    }
}
