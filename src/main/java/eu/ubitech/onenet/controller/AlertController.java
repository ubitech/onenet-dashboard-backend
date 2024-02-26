package eu.ubitech.onenet.controller;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import eu.ubitech.onenet.util.Mappings;
import eu.ubitech.onenet.service.AlertService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING)
public class AlertController {

    private final AlertService service;
    private final PropertiesConfiguration config;

    public AlertController(
            AlertService service,
            PropertiesConfiguration config) {
        this.service = service;
        this.config = config;
    }

    @GetMapping(value = "/alerts/dummy", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> dummy1(@RequestHeader String Authorization) {
        log.info("Alerts: Dummy endpoint called");
        return Map.of("message", "Alerts: Dummy endpoint called");
    }

    @GetMapping(
        path = {
            "/alerts/latest-alert-sse"
        })
    public SseEmitter emitAlerts() {
        log.debug("subscribing to alerts");
        SseEmitter emitter = new SseEmitter();
        ExecutorService sseMvcExecutor = Executors.newSingleThreadExecutor();
        sseMvcExecutor.execute(() -> {
            try {
                while (true) {
                    SseEventBuilder event = SseEmitter.event()
                            .data(service.getAbnormalIpAlert());
                    emitter.send(event);
                    Thread.sleep(1000L * config.getMinIntervalAlerts());
                }
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

}
