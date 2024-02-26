package eu.ubitech.onenet.config.jwt;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Jwt2AuthoritiesConverterImpl implements Jwt2AuthoritiesConverter{

    private PropertiesConfiguration config;

    public Jwt2AuthoritiesConverterImpl(PropertiesConfiguration config) {
        this.config = config;
    }

    @Override
    public Collection<? extends GrantedAuthority> convert(Jwt jwt) {

        try {
            List<String> realmAccess = (List<String>) ((Map<String, Object>) jwt.getClaim(
                    "realm_access")).get("roles");
            log.debug("realm_access roles: {}", realmAccess);

            List<String> clientAccess = (List<String>) ((Map<String, Object>) jwt.getClaimAsMap(
                    "resource_access").get("account")).get(
                    "roles");
            log.debug("resource_access roles: {}", clientAccess);
            // maybe claims of cliend id are needed, eg login-app

            return Stream
                    .concat(realmAccess.stream(),
                            clientAccess.stream()).map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        catch (Exception e){
            log.error("Could not get claims. Exception: {}", e.getMessage());
            return null;
        }

    }
}
