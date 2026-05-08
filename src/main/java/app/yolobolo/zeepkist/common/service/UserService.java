package app.yolobolo.zeepkist.common.service;

import app.yolobolo.zeepkist.common.model.User;
import app.yolobolo.zeepkist.common.model.UserIdentity;
import app.yolobolo.zeepkist.common.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService
{

    private final UserRepository userRepo;

    public Optional<User> findById(String id)
    {
        return userRepo.findById(id);
    }

    public List<User> findAll()
    {
        return userRepo.findAll();
    }

    public Optional<User> findByIdentity(String provider, String providerId)
    {
        return userRepo.findByIdentity(provider, providerId);
    }

    public User createOrUpdateUserFromIdentity(String provider, String providerId, String username, String avatarUrl)
    {
        Optional<User> existingUser = findByIdentity(provider, providerId);

        if (existingUser.isPresent())
        {
            User user = existingUser.get();
            user.setLastLoginAt(Instant.now());
            user.setAvatarUrl(avatarUrl);
            // Update username in identity if changed
            user.getIdentities().stream()
                    .filter(i -> provider.equalsIgnoreCase(i.getProvider()))
                    .forEach(i -> i.setUsername(username));
            return userRepo.save(user);
        }

        // Neuer User via Identity (z.B. Steam-Erstlogin)
        UserIdentity identity = UserIdentity.builder()
                .provider(provider)
                .providerId(providerId)
                .username(username)
                .linkedAt(Instant.now())
                .build();

        User newUser = User.builder()
                .displayName(username)
                .avatarUrl(avatarUrl)
                .token(java.util.UUID.randomUUID().toString().toUpperCase())
                .identities(new ArrayList<>(List.of(identity)))
                .roles(new ArrayList<>(List.of("ROLE_USER")))
                .createdAt(Instant.now())
                .lastLoginAt(Instant.now())
                .build();

        // Wenn Steam, füge ROLE_HOST hinzu (Zeepkist-Logik)
        if ("STEAM".equalsIgnoreCase(provider))
        {
            newUser.getRoles().add("ROLE_HOST");
        }

        return userRepo.save(newUser);
    }

    public User linkIdentity(User user, String provider, String providerId, String username)
    {
        // Check if identity is already linked to another user
        Optional<User> otherUser = findByIdentity(provider, providerId);
        if (otherUser.isPresent() && !otherUser.get().getId().equals(user.getId()))
        {
            throw new IllegalArgumentException("This account is already linked to another user.");
        }

        // Falls schon verknÃ¼pft, nur updaten
        Optional<UserIdentity> existingIdentity = user.getIdentities().stream()
                .filter(i -> provider.equalsIgnoreCase(i.getProvider()))
                .findFirst();

        if (existingIdentity.isPresent())
        {
            existingIdentity.get().setUsername(username);
        }
        else
        {
            user.getIdentities().add(UserIdentity.builder()
                    .provider(provider)
                    .providerId(providerId)
                    .username(username)
                    .linkedAt(Instant.now())
                    .build());

            // Logik-Zusatz: Steam-Link macht zum Host
            if ("STEAM".equalsIgnoreCase(provider) && !user.hasRole("ROLE_HOST"))
            {
                user.getRoles().add("ROLE_HOST");
            }
        }

        return userRepo.save(user);
    }
}
