package eu.ubitech.onenet.config.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

public interface Jwt2AuthenticationConverter extends Converter<Jwt, AbstractAuthenticationToken> {
}
