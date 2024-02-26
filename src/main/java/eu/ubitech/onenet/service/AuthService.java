package eu.ubitech.onenet.service;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import eu.ubitech.onenet.dto.LoginDto;
import eu.ubitech.onenet.dto.RefreshTokenDto;
import eu.ubitech.onenet.dto.RegisterDto;
import eu.ubitech.onenet.exceptions.AuthenticationServerException;
import eu.ubitech.onenet.exceptions.CredentialsException;
import eu.ubitech.onenet.exceptions.UserExistsException;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

@Slf4j
@Service
public class AuthService {

    private final PropertiesConfiguration config;
    private final WebClient client;

    private String tokenUri;
    private String logoutUri;

    @Autowired
    public AuthService(PropertiesConfiguration config, Builder builder) {
        this.config = config;
        this.client = builder.build();
    }

    @PostConstruct
    public void initStrings() {
        String baseUri = config.getKeycloak().getAuthServerUrl()
                .concat("/realms/")
                .concat(config.getKeycloak().getRealm())
                .concat("/protocol/openid-connect/");
        // eg 'http://localhost:8080/auth/realms/main-authentication/protocol/openid-connect/token'
        tokenUri = baseUri.concat("token");
        logoutUri = baseUri.concat("logout");
    }

    /**
     * Authentication Service Login
     *
     * @param loginDto - original object with values
     * @return string of JWT token
     */
    public String login(LoginDto loginDto) {
        log.info("Started token");
        try {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("username", loginDto.getUsername());
            map.add("password", loginDto.getPassword());
            map.add("client_id", config.getMyKeyClient().getClientId());
            map.add("grant_type", config.getMyKeyClient().getAuthorizationGrantType());

            ResponseSpec retrieve = client.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(map))
                    .retrieve();

            String tokenStr = retrieve.bodyToMono(String.class).block();
            log.debug("token: {}", tokenStr);

            return tokenStr;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            throw new CredentialsException();
        }
    }

    /**
     * Authentication Service Logout
     *
     * @param refreshTokenDto - refresh token object
     */
    public void logout(RefreshTokenDto refreshTokenDto) {
        try {
            log.info("started logout");
            log.debug("logout refreshToken: {}", refreshTokenDto.getRefresh_token());

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", config.getMyKeyClient().getClientId());
            map.add("refresh_token", refreshTokenDto.getRefresh_token());

            ResponseSpec retrieve = client.post()
                    .uri(logoutUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(map))
                    .retrieve();

        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            throw new CredentialsException();
        }
    }

    /**
     * Authentication Service refresh
     *
     * @param refreshTokenDto - refresh token object
     * @return full JWT
     */
    public String refresh(RefreshTokenDto refreshTokenDto) {
        try {
            log.info("started refresh");
            log.debug("refresh refreshToken: {}", refreshTokenDto.getRefresh_token());

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("client_id", config.getMyKeyClient().getClientId());
            map.add("grant_type", "refresh_token");
            map.add("refresh_token", refreshTokenDto.getRefresh_token());

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map,
                    new HttpHeaders());
            ResponseSpec retrieve = client.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(map))
                    .retrieve();

            String tokenStr = retrieve.bodyToMono(String.class).block();
            log.debug("new token: {}", tokenStr);
            return tokenStr;
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            throw new CredentialsException();
        }
    }

    private Keycloak keycloak() {
        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(config.getKeycloak().getAuthServerUrl())
                .realm("master")  // always fixed
                .username(config.getMyKeyClient().getAdminUser())
                .password(config.getMyKeyClient().getAdminPass())
                .clientId("admin-cli")  // always fixed
//                .clientSecret(secret) // in case needed in the future
                .build();
        return keycloak;
    }

    /**
     * Authentication Service Register The function uses keycloak admin cli to perform the
     * operation
     *
     * @param registerDto - dto with register information
     */
    public void addUserKeycloak(RegisterDto registerDto) {
        try {
            log.info("Started register");
            Keycloak keycloak = keycloak();

            UserRepresentation keycloakUser = new UserRepresentation();
            keycloakUser.setEnabled(true);
            keycloakUser.setUsername(registerDto.getUsername());
            keycloakUser.setEmail(registerDto.getEmail());
            keycloakUser.setEmailVerified(true);

            RealmResource realmResource = keycloak.realm(config.getKeycloak().getRealm());
            UsersResource usersResource = realmResource.users();

            // Create user
            Response response = usersResource.create(keycloakUser);
            log.info("responseStatus: {}  responseStatusInfo {}",
                    response.getStatus(), response.getStatusInfo());
            if (response.getStatus() == 404) {
                log.error(
                        "Connection to keycloak is not possible with 404, most probable error cause "
                                + "is realm is not loaded or realm/resource configuration is incorrect");
                throw new AuthenticationServerException();
            } else if (response.getStatus() == 409) {
                log.warn("User already exists");
                throw new UserExistsException();
            } else if (response.getStatus() != 201) {
                log.error("error response with status with status: {} and message: {}",
                        response.getStatus(), response.getStatusInfo());
                throw new AuthenticationServerException();
            }

            String userId = CreatedResponseUtil.getCreatedId(response);
            log.debug("keycloak user created with id: {}", userId);

            // assign roles
            UserResource userResource = usersResource.get(userId);
            List<RoleRepresentation> roleRepresentations = registerDto.getRealmRoles().stream()
                    .map(roleName -> realmResource.roles().get(roleName).toRepresentation())
                    .collect(Collectors.toList());
            userResource.roles().realmLevel().add(roleRepresentations);

            // set password
            CredentialRepresentation passwordCred = new CredentialRepresentation();
            passwordCred.setTemporary(false);
            passwordCred.setType(CredentialRepresentation.PASSWORD);
            passwordCred.setValue(registerDto.getPassword());

            userResource = usersResource.get(userId);
            userResource.resetPassword(passwordCred);

            keycloak().close();
            log.info("keycloak user password updated");
        }
        // we have to set this re-catch, as we have to handle user creation
        // 404 not found, which are our exceptions, but also we have to handle system exceptions
        // that are thrown by the keycloak admin functions
        catch (UserExistsException e) {
            throw new UserExistsException();
        } catch (Exception e) {
            log.error("Exception: {}", e.getMessage());
            throw new AuthenticationServerException();
        }
    }
}
