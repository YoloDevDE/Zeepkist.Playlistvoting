package app.yolobolo.zeepkist.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentity
{
    private String provider; // STEAM, TWITCH, GOOGLE, etc.
    private String providerId; // Die ID des Providers (z.B. SteamID64)
    private String username; // Anzeigename des Providers
    private Instant linkedAt;
}
