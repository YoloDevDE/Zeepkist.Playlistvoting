package app.yolobolo.zeepkist.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("users")
public class User
{
    @Id
    private String id;

    @Indexed(unique = true, sparse = true)
    private String token;

    private String displayName;

    @Builder.Default
    private List<UserIdentity> identities = new ArrayList<>();

    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Builder.Default
    private List<String> managerIds = new ArrayList<>();

    private Instant createdAt;
    private Instant lastLoginAt;

    public boolean hasRole(String role)
    {
        return roles != null && roles.contains(role);
    }

    public boolean hasSteamLinked()
    {
        return identities != null && identities.stream()
                .anyMatch(i -> "STEAM".equalsIgnoreCase(i.getProvider()));
    }

    public String getSteamId()
    {
        if (identities == null)
        {
            return null;
        }
        return identities.stream()
                .filter(i -> "STEAM".equalsIgnoreCase(i.getProvider()))
                .map(UserIdentity::getProviderId)
                .findFirst()
                .orElse(null);
    }
}
