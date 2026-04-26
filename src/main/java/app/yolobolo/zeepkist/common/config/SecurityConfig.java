package app.yolobolo.zeepkist.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@lombok.RequiredArgsConstructor
public class SecurityConfig
{

    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain playlistVotingSecurityFilterChain(HttpSecurity http)
    {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/playlistvoting", "/playlistvoting/dashboard", "/navigation", "/error", "/login", "/css/**", "/js/**", "/api/auth/steam/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/playlistvoting/dashboard/live").permitAll()
                        .requestMatchers("/api/playlistvoting/vote"
                                , "/api/playlistvoting/result"
                                , "/api/playlistvoting/currentLevel/**"
                                , "/api/playlistvoting/playlist"
                                , "/api/playlistvoting/reset").authenticated() // API Endpunkte für das Spiel/Public - nun via Token/Session auth
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/playlistvoting/**", "/playlistvoting/dashboard/session/**", "/api/auth/steam/**")
                )
                .headers(headers -> headers
                        .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
