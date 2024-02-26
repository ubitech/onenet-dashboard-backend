package eu.ubitech.onenet.controller;

import eu.ubitech.onenet.dto.LoginDto;
import eu.ubitech.onenet.dto.RefreshTokenDto;
import eu.ubitech.onenet.dto.RegisterDto;
import eu.ubitech.onenet.service.AuthService;
import eu.ubitech.onenet.util.Mappings;
import java.util.Map;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = Mappings.LOGIN, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public String login(@Valid @RequestBody LoginDto loginDto) {
        return authService.login(loginDto);
    }

    @PostMapping(value = Mappings.LOGOUT, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> logout(@Valid @RequestBody RefreshTokenDto refreshTokenDto) {
        authService.logout(refreshTokenDto);
        return Map.of("message", "User logged out successfully");
    }

    @PostMapping(value = Mappings.REFRESH, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public String refresh(@Valid @RequestBody RefreshTokenDto refreshTokenDto) {
        return authService.refresh(refreshTokenDto);
    }

    @PostMapping(value = Mappings.REGISTER, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> register(@Valid @RequestBody RegisterDto registerDto) {
        authService.addUserKeycloak(registerDto);
        return Map.of("message", "User created successfully");
    }
}