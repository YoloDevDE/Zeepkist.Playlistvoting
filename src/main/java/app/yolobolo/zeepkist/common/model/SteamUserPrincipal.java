package app.yolobolo.zeepkist.common.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;

@Getter
@Builder
public class SteamUserPrincipal implements UserDetails
{
    private final String hostId;
    private final String steamId;
    private final String steamName;
    private final String displayName;
    private final String token;
    private final Collection<? extends GrantedAuthority> authorities;

    public static SteamUserPrincipal fromUser(User user)
    {
        return SteamUserPrincipal.builder()
                .hostId(user.getId())
                .steamId(user.getSteamId())
                .steamName(user.getDisplayName())
                .displayName(user.getDisplayName())
                .token(user.getToken())
                .authorities(user.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities()
    {
        return authorities;
    }

    @Override
    public String getPassword()
    {
        return null;
    }

    @Override
    public String getUsername()
    {
        return steamName;
    }

    @Override
    public boolean isAccountNonExpired()
    {
        return true;
    }

    @Override
    public boolean isAccountNonLocked()
    {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired()
    {
        return true;
    }

    @Override
    public boolean isEnabled()
    {
        return true;
    }
}
