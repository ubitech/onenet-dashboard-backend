package eu.ubitech.onenet.config;

import eu.ubitech.onenet.config.jwt.Jwt2AuthenticationConverter;
import eu.ubitech.onenet.util.Mappings;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            Jwt2AuthenticationConverter authenticationConverter,
            ServerProperties serverProperties)
            throws Exception {

        // Enable OAuth2 with custom authorities mapping
        http.oauth2ResourceServer().jwt().jwtAuthenticationConverter(authenticationConverter);
        http.anonymous();
        http.cors().configurationSource(corsConfigurationSource());
        http.csrf().disable();
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

        // If SSL enabled, disable http (https only)
        if (serverProperties.getSsl() != null && serverProperties.getSsl().isEnabled()) {
            http.requiresChannel().anyRequest().requiresSecure();
        } else {
            http.requiresChannel().anyRequest().requiresInsecure();
        }

        http.authorizeHttpRequests()
                .antMatchers("/actuator/health/**", "/v3/api-docs", "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html").permitAll()
                .antMatchers(Mappings.APIMAPPING + Mappings.LOGIN,
                        Mappings.APIMAPPING + Mappings.LOGOUT,
                        Mappings.APIMAPPING + Mappings.REGISTER,
                        Mappings.APIMAPPING + Mappings.REFRESH).permitAll()
                .antMatchers(Mappings.APIMAPPING + "/test/anonymous").permitAll()
                .antMatchers(Mappings.APIMAPPING + "test/consumer").hasAnyAuthority("consumer")
                .antMatchers(Mappings.APIMAPPING + "/monitoring/network/trigger-transactions")
                .permitAll()
                .antMatchers(
                        Mappings.APIMAPPING + "/monitoring/network/http-transactions-sse-periodic")
                .permitAll()
                .antMatchers(
                        Mappings.APIMAPPING + "/alerts/latest-alert-sse")
                .permitAll()
                .anyRequest().authenticated();

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        final var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("*"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("*"));

        final var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
