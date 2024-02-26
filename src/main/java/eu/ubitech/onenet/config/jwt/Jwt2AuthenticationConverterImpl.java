package eu.ubitech.onenet.config.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Jwt2AuthenticationConverterImpl implements Jwt2AuthenticationConverter{

    private Jwt2AuthoritiesConverter authoritiesConverter;

    public Jwt2AuthenticationConverterImpl(
            Jwt2AuthoritiesConverter authoritiesConverter) {
        this.authoritiesConverter = authoritiesConverter;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, authoritiesConverter.convert(jwt));
    }
}
