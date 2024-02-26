package eu.ubitech.onenet.config.jwt;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public interface Jwt2AuthoritiesConverter extends
        Converter<Jwt, Collection<? extends GrantedAuthority>> {
}
