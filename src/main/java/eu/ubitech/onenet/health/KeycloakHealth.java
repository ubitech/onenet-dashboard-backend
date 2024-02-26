package eu.ubitech.onenet.health;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

@Slf4j
@Component("KeycloakHealth")
public class KeycloakHealth implements HealthIndicator {

    private final PropertiesConfiguration config;
    private final WebClient client;

    @Autowired
    public KeycloakHealth(PropertiesConfiguration config, WebClient.Builder builder) {
        this.config = config;
        this.client = builder.build();
    }


    @Override
    public Health health() {

        Builder healthStatus;

        try {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("username", config.getMyKeyClient().getAdminUser());
            map.add("password", config.getMyKeyClient().getAdminPass());
            map.add("client_id", "admin-cli");
            map.add("grant_type", "password");

            // eg http://localhost:8090/auth/realms/master/protocol/openid-connect/token
            String tokenUri = config.getKeycloak().getAuthServerUrl()
                    .concat("/realms/master")
                    .concat("/protocol/openid-connect/token");

            // Attempt to retrieve a token
            ResponseSpec retrieve = client.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(map))
                    .retrieve();

            HttpStatus status = retrieve.toBodilessEntity().block().getStatusCode();

            if (status == HttpStatus.OK){
                log.debug("keycloak health check success");
                healthStatus = Health.up();
            } else {
                log.error("health check keycloak failed with return code: {}", status);
                healthStatus = Health.down();
            }

        } catch (Exception e) {
            log.error("health check keycloak failed, no connection, Exception: {}", e.getMessage());
            healthStatus = Health.down();
        }

        return healthStatus.build();
    }
}


