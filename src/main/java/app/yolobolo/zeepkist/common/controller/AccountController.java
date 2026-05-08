package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.apps.playlistvoting.service.VoteService;
import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import app.yolobolo.zeepkist.common.model.User;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;
import java.util.Objects;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class AccountController
{

    private final VoteService voteService;

    @GetMapping
    public String profile(Model model, @AuthenticationPrincipal SteamUserPrincipal principal, HttpServletRequest request)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        User user = principal != null ? voteService.findUserById(principal.getHostId()) : null;
        if (user == null && principal != null)
        {
            log.warn("Profile accessed by authenticated user {}, but no user record found in database for hostId {}",
                    principal.getDisplayName(), principal.getHostId());
        }

        List<User> managers = (user != null && user.getManagerIds() != null)
                ? user.getManagerIds().stream()
                  .filter(Objects::nonNull)
                  .map(voteService::findUserBySteamId)
                  .filter(Objects::nonNull)
                  .toList()
                : List.of();

        model.addAttribute("host", user);
        model.addAttribute("managers", managers);
        model.addAttribute("username", principal != null ? principal.getDisplayName() : "Unknown");

        String hostId = principal != null ? principal.getHostId() : null;
        model.addAttribute("sessionCount", hostId != null ? voteService.countSessionsByHostId(hostId) : 0);
        model.addAttribute("totalVotes", hostId != null ? voteService.countTotalVotesByHostId(hostId) : 0);
        model.addAttribute("voteStats", hostId != null ? voteService.getVoteStatsByHostId(hostId) : Map.of());

        String baseUrl = request.getScheme() + "://" + request.getServerName();
        if ((request.getScheme().equals("http") && request.getServerPort() != 80) ||
                (request.getScheme().equals("https") && request.getServerPort() != 443))
        {
            baseUrl += ":" + request.getServerPort();
        }
        model.addAttribute("baseUrl", baseUrl);

        return "common/profile";
    }

    @PostMapping("/manager/add")
    public String addManager(@RequestParam String steamId, @AuthenticationPrincipal SteamUserPrincipal principal)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        String hostId = principal != null ? principal.getHostId() : null;
        if (hostId == null)
        {
            return "redirect:/login";
        }
        User user = voteService.findUserById(hostId);
        if (user != null && user.getManagerIds() != null && !user.getManagerIds().contains(steamId))
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

        String hostId = principal != null ? principal.getHostId() : null;
        if (hostId == null)
        {
            return "redirect:/login";
        }
        User user = voteService.findUserById(hostId);
        if (user != null && user.getManagerIds() != null)
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

        String hostId = principal != null ? principal.getHostId() : null;
        if (hostId == null)
        {
            return "redirect:/login";
        }
        User user = voteService.findUserById(hostId);
        if (user != null)
        {
            user.setToken(java.util.UUID.randomUUID().toString().toUpperCase());
            voteService.saveUser(user);
            log.info("Token refreshed for host user {}", hostId);
        }
        return "redirect:/profile";
    }

    @PostMapping("/delete")
    public String deleteProfile(@AuthenticationPrincipal SteamUserPrincipal principal, HttpServletRequest request)
    {
        if (principal == null)
        {
            return "redirect:/login";
        }

        String hostId = principal != null ? principal.getHostId() : null;
        if (hostId == null)
        {
            return "redirect:/login";
        }
        voteService.deleteUser(hostId);
        log.info("Profile deleted for host user {}", hostId);

        try
        {
            request.logout();
        }
        catch (Exception e)
        {
            log.error("Failed to logout after profile deletion", e);
        }

        return "redirect:/login?deleted=true";
    }
}
