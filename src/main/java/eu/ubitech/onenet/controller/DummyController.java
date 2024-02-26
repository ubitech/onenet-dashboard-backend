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
 * This is just a dummy controller to be called by frontend so we can log various paths
 * for the geolocation algorithm etc..
 * This file must be totally removed when the solution is operational.
 */
@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING + "/monitoring")
public class DummyController {

    @GetMapping(value = "/network", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dummy1(@RequestHeader String Authorization) {
        log.info("Network monitoring analysis called");
        return Map.of("message", "Network monitoring analysis called");
    }

    @GetMapping(value = "/energy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dummy2(@RequestHeader String Authorization) {
        log.info("Energy analytics called");
        return Map.of("message", "Energy analytics called");
    }

    @GetMapping(value = "/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dummy3(@RequestHeader String Authorization) {
        log.info("Data sharing analytics called");
        return Map.of("message", "Data sharing analytics called");
    }

    @GetMapping(value = "/market", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dummy4(@RequestHeader String Authorization) {
        log.info("Market called");
        return Map.of("message", "Market called");
    }
}
