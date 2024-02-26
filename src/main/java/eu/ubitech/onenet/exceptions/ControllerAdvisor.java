package eu.ubitech.onenet.exceptions;

import eu.ubitech.onenet.service.AnalyticsCommunicationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ControllerAdvisor extends ResponseEntityExceptionHandler {

    /**
     * Exception with status 422 Unprocessable entity, means the request has correct syntax but not content
     */
    @ExceptionHandler(CredentialsException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Map<String, String> handleCredentialsException(
            CredentialsException ex, WebRequest request) {
        return Map.of("message", "Wrong credentials provided");
    }

    /**
     * Exception with status 500
     */
    @ExceptionHandler(InternalErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleInternalErrorException(
            InternalErrorException ex, WebRequest request) {
        return Map.of("message", "Internal error");
    }

    /**
     * Exception with status 409 CONFLICT thrown when a user already exists in keycloak
     */
    @ExceptionHandler(UserExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleUserExistsException(
            UserExistsException ex, WebRequest request) {
        return Map.of("message", "User already exists");
    }

    /**
     * Exception thrown when connection to keycloak is not possible
     */
    @ExceptionHandler(AuthenticationServerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleAuthenticationServerException(
            AuthenticationServerException ex, WebRequest request) {
        return Map.of("message", "Cannot connect to authentication server");
    }

    /**
     * Exception thrown when cannot contact analytics service
     */
    @ExceptionHandler(AnalyticsCommunicationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, String> handleAnalyticsCommunicationException(
            AnalyticsCommunicationException ex, WebRequest request) {
        return Map.of("message", "Cannot connect analytics service");
    }
}
