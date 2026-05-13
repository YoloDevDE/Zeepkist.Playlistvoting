package app.yolobolo.zeepkist.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig
{

    private final TokenAuthenticationFilter tokenAuthenticationFilter;

    @Bean
    public SecurityFilterChain playlistVotingSecurityFilterChain(HttpSecurity http)
    {
        http
                .authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers(
                                        "/",
                                        "/api/auth/steam/**",
                                        "/api/auth/validate",
                                        "/api/playlistvoting/dashboard",
                                        "/api/playlistvoting/votes",
                                        "/auth/**",
                                        "/css/**",
                                        "/error",
                                        "/health",
                                        "/js/**",
                                        "/login",
                                        "/logout",
                                        "/navigation",
                                        "/playlistvoting/**",
                                        "/swagger-ui.html",
                                        "/swagger-ui/**",
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs.yaml",
                                        "/ws-dashboard/**"
                                ).permitAll()

                                .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", "/ws-dashboard/**")
                )
                .headers(headers -> headers
                        .frameOptions(org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
