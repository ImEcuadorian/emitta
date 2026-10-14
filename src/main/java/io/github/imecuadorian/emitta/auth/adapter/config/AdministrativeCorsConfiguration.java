package io.github.imecuadorian.emitta.auth.adapter.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
import java.util.*;

/** Exact origins only. Bearer authentication, no browser credential cookies. Empty by default. */
@Configuration(proxyBeanMethods=false)
public class AdministrativeCorsConfiguration {
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${emitta.security.allowed-web-origins:}") String origins) {
        CorsConfiguration config=new CorsConfiguration();
        var allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        for(var origin:allowed) {
            var uri=java.net.URI.create(origin);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null
                    || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPath()!=null && !uri.getPath().isEmpty()) || origin.contains("*"))
                throw new IllegalArgumentException("Browser origin must be an exact HTTPS origin");
        }
        config.setAllowedOrigins(allowed);
        config.setAllowCredentials(false);
        config.setAllowedMethods(List.of("GET","POST","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type","Idempotency-Key"));
        config.setExposedHeaders(List.of("Retry-After"));
        config.setMaxAge(300L);
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/v1/**",config);return source;
    }
}
