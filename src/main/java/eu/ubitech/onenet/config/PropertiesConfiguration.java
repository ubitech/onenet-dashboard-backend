package eu.ubitech.onenet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties
public class PropertiesConfiguration {

    private KeycloakProperties keycloak = new KeycloakProperties();
    private MyKeyClientProperties myKeyClient = new MyKeyClientProperties();
    private RateLimitProperties rateLimit = new RateLimitProperties();

    private int minInterval;
    private int minIntervalAlerts;

    private String elasticUrl;
    private String elasticUser;
    private String elasticPass;

    private String analyticsUrl;

    @Getter
    @Setter
    public static class KeycloakProperties {
        private String authServerUrl;
        private String realm;
        private String resource;
    }

    @Getter
    @Setter
    public static class MyKeyClientProperties {
        private String clientId;
        private String scope;
        private String authorizationGrantType;
        private String adminUser;
        private String adminPass;
    }

    @Getter
    @Setter
    public static class RateLimitProperties {
        private int capacity;
        private int tokenRefill;
        private int refillIntervalInMinutes;
    }
}
