package app.yolobolo.zeepkist.common.controller.advice;

import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributes
{

    @ModelAttribute
    public void addAuthAttributes(Model model,
                                  @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        model.addAttribute("loggedIn", principal != null);

        if (principal == null)
        {
            return;
        }

        model.addAttribute("hostId", principal.getHostId());
        model.addAttribute("steamId", principal.getSteamId());
        model.addAttribute("steamName", principal.getSteamName());
        model.addAttribute("displayName", principal.getDisplayName());
        model.addAttribute("token", principal.getToken());
    }
}
