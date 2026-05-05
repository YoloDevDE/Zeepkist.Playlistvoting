package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class AccountController
{

    private final VoteService voteService;

    @GetMapping
    public String profile(Model model, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        User user = voteService.findUserById(principal.getHostId());

        List<User> managers = user != null && user.getManagerIds() != null
                ? user.getManagerIds().stream()
                  .map(voteService::findUserBySteamId)
                  .filter(Objects::nonNull)
                  .toList()
                : List.of();

        model.addAttribute("host", user);
        model.addAttribute("managers", managers);
        model.addAttribute("username", principal.getDisplayName());

        return "common/profile";
    }

    @PostMapping("/manager/add")
    public String addManager(@RequestParam String steamId, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        String hostId = principal.getHostId();
        User user = voteService.findUserById(hostId);
        if (user != null && !user.getManagerIds().contains(steamId))
        {
            user.getManagerIds().add(steamId);
            voteService.saveUser(user);
            log.info("Added manager steamId {} to host user {}", steamId, hostId);
        }
        return "redirect:/profile";
    }

    @PostMapping("/manager/remove")
    public String removeManager(@RequestParam String steamId, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        String hostId = principal.getHostId();
        User user = voteService.findUserById(hostId);
        if (user != null)
        {
            user.getManagerIds().remove(steamId);
            voteService.saveUser(user);
            log.info("Removed manager steamId {} from host user {}", steamId, hostId);
        }
        return "redirect:/profile";
    }

    @PostMapping("/token/refresh")
    public String refreshToken(@AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        String hostId = principal.getHostId();
        User user = voteService.findUserById(hostId);
        if (user != null)
        {
            user.setToken(java.util.UUID.randomUUID().toString().toUpperCase());
            voteService.saveUser(user);
            log.info("Token refreshed for host user {}", hostId);
        }
        return "redirect:/profile";
    }
}
