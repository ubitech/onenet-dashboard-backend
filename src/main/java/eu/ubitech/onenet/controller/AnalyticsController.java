package eu.ubitech.onenet.controller;

import eu.ubitech.onenet.service.AnalyticsCommunicationService;
import eu.ubitech.onenet.util.Mappings;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING)
public class AnalyticsController {

    private final AnalyticsCommunicationService service;

    public AnalyticsController(AnalyticsCommunicationService service) {
        this.service = service;
    }

    @GetMapping(
        value = {
            "/analytics/anomaly_detection",
            "/analytics/anomaly_detection/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Object getAnalytics(@RequestHeader String Authorization, @PathVariable Optional<String> connector, @RequestParam(name = "minutes", required = false, defaultValue = "60") String minutes) {
        return service.getAnomalyResults(connector.isPresent() ? connector.get() : null, minutes);
    }

    @GetMapping(value = "/analytics/security_report", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Object getSecurityReport(@RequestHeader String Authorization) {
        return service.getSecurityReport();
    }
}
