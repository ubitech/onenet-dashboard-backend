package eu.ubitech.onenet.controller;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import eu.ubitech.onenet.dto.HttpTransactionsDto;
import eu.ubitech.onenet.dto.AdvancedFilteringDto;
import eu.ubitech.onenet.service.NetworkMonitoringService;
import eu.ubitech.onenet.util.Mappings;
import eu.ubitech.onenet.model.ConnectorLogs;
import eu.ubitech.onenet.model.CountryHitsCount;
import eu.ubitech.onenet.model.StackedSeriesDataPoint;
import eu.ubitech.onenet.model.AdvancedFilteringResult;
import eu.ubitech.onenet.model.HealthCheckResult;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@RestController
@RequestMapping(Mappings.APIMAPPING)
public class NetworkMonitoringController {

    private final NetworkMonitoringService service;
    private final PropertiesConfiguration config;

    public NetworkMonitoringController(NetworkMonitoringService service,
            PropertiesConfiguration config) {
        this.service = service;
        this.config = config;
    }

    @GetMapping(
        value = {
            "/monitoring/network/http-monthly",
            "/monitoring/network/http-monthly/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public HttpTransactionsDto getHttpMonthly(@RequestHeader String Authorization, @PathVariable Optional<String> connector) {
        log.info("get http monthly called");
        return service.queryLastMonth(connector.isPresent() ? connector.get() : null);
    }

    @GetMapping(
        value = {
            "/monitoring/network/http-hourly",
            "/monitoring/network/http-hourly/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public HttpTransactionsDto getHttp24HourStatic(@RequestHeader String Authorization, @PathVariable Optional<String> connector) {
        log.info("get http 24 hourly called");
        return service.query24hourEntriesCount(connector.isPresent() ? connector.get() : null);
    }

    @GetMapping(
        path = {
            "/monitoring/network/http-transactions-sse-periodic"
        })
    public SseEmitter handleRbe(@RequestParam(name = "connector", required = false, defaultValue = "") String connector) {
        log.debug("get http live events called with connector [{}]", (connector.length() == 0 ? "*" : connector));
        SseEmitter emitter = new SseEmitter();
        ExecutorService sseMvcExecutor = Executors.newSingleThreadExecutor();
        sseMvcExecutor.execute(() -> {
            try {
                while (true) {
                    SseEventBuilder event = SseEmitter.event()
                            .data(service.query24hourEntriesCount(connector.length() == 0 ? null : connector));
                    emitter.send(event);
                    Thread.sleep(1000L * config.getMinInterval());
                }
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @GetMapping(
        value = {
            "/monitoring/network/http-monthly-per-country",
            "/monitoring/network/http-monthly-per-country/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<CountryHitsCount> getHttpMonthlyPerCountry(@RequestHeader String Authorization, @PathVariable Optional<String> connector) {
        log.debug("get http monthly per country called with connector [{}]", connector.isPresent() ? connector.get() : "*");
        return service.aggregateHitsPerCountry(connector.isPresent() ? connector.get() : null);
    }

    @GetMapping(
        value = {
            "/monitoring/network/http-bytes-sent",
            "/monitoring/network/http-bytes-sent/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public HttpTransactionsDto getHttpBytesSent(@RequestHeader String Authorization, @PathVariable Optional<String> connector) {
        log.debug("get http bytes sent called with connector [{}]", connector.isPresent() ? connector.get() : "*");
        return service.aggregateRecentBytesSent(connector.isPresent() ? connector.get() : null);
    }

    @GetMapping(
        value = {
            "/monitoring/network/http-response-codes",
            "/monitoring/network/http-response-codes/{connector}"
        },
        produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<StackedSeriesDataPoint> getHttpResponseCodes(@RequestHeader String Authorization, @PathVariable Optional<String> connector) {
        log.debug("get http response codes called with connector [{}]", connector.isPresent() ? connector.get() : "*");
        return service.aggregateRecentResponseCodes(connector.isPresent() ? connector.get() : null);
    }

    @GetMapping(value = "/monitoring/network/connectors", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<String> getConnectorList(@RequestHeader String Authorization) {
        log.debug("get connector list called");
        return service.queryConnectors();
    }

    @GetMapping(value = "/monitoring/network/connectors-health-check", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<HealthCheckResult> getConnectorsHealthCheck(@RequestHeader String Authorization) {
        log.debug("get connectors health check called");
        return service.getHealthCheck();
    }

    @PostMapping(value = "/monitoring/network/advanced-filtering", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public List<AdvancedFilteringResult> getAdvancedFilteringResults(@RequestHeader String Authorization, @Valid @RequestBody AdvancedFilteringDto advancedFilteringDto) {
        log.debug("get advanced filtering results called");
        return service.doAdvancedFiltering(advancedFilteringDto);
    }
}
