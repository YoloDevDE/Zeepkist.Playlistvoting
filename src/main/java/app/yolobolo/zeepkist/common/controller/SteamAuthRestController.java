package app.yolobolo.zeepkist.common.controller;

import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth/steam")
@RequiredArgsConstructor
public class SteamAuthRestController
{
    private final AuthService authService;

    @PostMapping("/ticket")
    public ResponseEntity<Map<String, String>> loginWithTicket(@RequestBody String ticketHex)
    {
        log.info("Received Steam auth ticket login request");
        String steamId = authService.verifySteamTicket(ticketHex);

        if (steamId != null)
        {
            log.info("Steam ticket verified successfully for steamId: {}", steamId);
            User user = authService.findOrCreateUser(steamId);

            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "token", user.getToken(),
                    "displayName", user.getDisplayName(),
                    "steamId", steamId
            ));
        }

        log.warn("Steam ticket verification failed");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
