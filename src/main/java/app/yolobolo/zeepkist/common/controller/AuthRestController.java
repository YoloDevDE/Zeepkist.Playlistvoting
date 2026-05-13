package app.yolobolo.zeepkist.common.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthRestController
{
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(Principal principal)
    {
        if (principal != null)
        {
            log.info("Token validation successful for user: {}", principal.getName());
            return ResponseEntity.ok().build();
        }
        log.warn("Token validation failed: No valid principal found");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
