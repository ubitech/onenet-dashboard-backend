package eu.ubitech.onenet.controller;

import eu.ubitech.onenet.util.Mappings;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints starting with /test are for connectivity checks
 * - /anonymous checks if connection to service is possible
 * - /consumer checks if the user is authorized to view an API, if the
 * authorization succeeded
 */
@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING + "/test")
public class AppController {

    @GetMapping(value = "/anonymous", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> getAnonymous() {

        log.info("getAnonymous called");

        log.info("info log");
        log.trace("trace log");
        log.debug("debug log");
        log.error("error log");
        log.warn("warn log");

        return Map.of("message", "Hello Anonymous");
    }

    @GetMapping(value = "/consumer", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> greetConsumer(@RequestHeader String Authorization) {
        log.info("Hello Consumer called");
        return Map.of("message", "Hello Consumer");
    }

}
