package app.yolobolo.zeepkist.common.config;

import app.yolobolo.zeepkist.common.model.SteamUserPrincipal;
import jakarta.annotation.PostConstruct;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

/**
 * Configures springdoc-openapi to ignore Spring Security principal types when
 * generating the OpenAPI schema. Without this, springdoc tries to introspect
 * {@link SteamUserPrincipal} (which exposes a {@code Collection<? extends GrantedAuthority>})
 * as a request parameter, producing a Schema whose {@code properties} map is null and
 * causing a NullPointerException when rendering Swagger UI / {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig
{

    @PostConstruct
    public void configureSpringDoc()
    {
        SpringDocUtils.getConfig()
                .addRequestWrapperToIgnore(SteamUserPrincipal.class);
    }
}
