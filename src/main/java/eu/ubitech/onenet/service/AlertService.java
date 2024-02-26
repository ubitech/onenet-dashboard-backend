package eu.ubitech.onenet.service;

import eu.ubitech.onenet.dto.AlertDto;
import eu.ubitech.onenet.config.PropertiesConfiguration;
import eu.ubitech.onenet.exceptions.AnalyticsCommunicationException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.TimeZone;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

@Slf4j
@Service
public class AlertService {

    private final PropertiesConfiguration config;
    private final WebClient analyticsClient;
    private final String analyticsUrl;

    // Hold abnormal IPs in memory
    private ArrayList<String> lastAbnormalIps = new ArrayList<String>();
    // Record the last time an update was made in this variable
    private long lastUpdateSecondsFromEpoch = 0;
    private final long secondsUntilStale = 120;

    public AlertService(
            PropertiesConfiguration config,
            Builder builder
            ) {
        this.config = config;
        this.analyticsUrl = config.getAnalyticsUrl();
        this.analyticsClient = builder.build();
    }

    /**
     * Function that DTO of Alerts, used for live data for SSE
     *
     * @return AlertDto
     */
    public AlertDto getAbnormalIpAlert() {
        log.debug("Starting getAbnormalIpAlert");

        AlertDto dto = new AlertDto();

        try {
            Instant instant = Instant.now();
            long nowSecondsFromEpoch = instant.getEpochSecond();

            log.debug("now {}", nowSecondsFromEpoch);
            log.debug("lup {}", lastUpdateSecondsFromEpoch);

            // Do not query anomaly_detection more than once per `secondsUntilStale`
            // Return last result instead, stored in lastAbnormalIps
            if (nowSecondsFromEpoch - lastUpdateSecondsFromEpoch <= secondsUntilStale) {
                log.debug("data is fresh, returning last data {}", lastAbnormalIps);
                dto.setAbnormalIps(lastAbnormalIps);
                return dto;
            }

            String uri = analyticsUrl.concat("/api/v1/analytics/anomaly_detection/get_predictions/");

            ResponseSpec retrieve = analyticsClient.get()
                    .uri(uri)
                    .header("minutes", "60")
                    .retrieve();

            HttpStatus status = retrieve.toBodilessEntity().block().getStatusCode();
            Object result = retrieve.bodyToMono(Object.class).block();

            if (status != HttpStatus.OK) {
                log.error("Analytics returned status: {}", status);
                throw new AnalyticsCommunicationException();
            }

            log.debug("analytics result is");
            log.debug(result.toString());

            ArrayList<String> abnormalIps = new ArrayList<String>();

            // Populate abnormalIps array
            for (Object timeslot: (ArrayList)result) {
                List<String> ips = (List<String>) ((LinkedHashMap) timeslot).get("ip");
                List<Double> ip_statuses = (List<Double>) ((LinkedHashMap) timeslot).get("ip_status");
                for (int i = 0; i < ips.size(); i++) {
                    // log.debug(ips.get(i).toString());
                    // log.debug(ip_statuses.get(i).toString());

                    // Negative status means IP behavior was abnormal
                    if (ip_statuses.get(i) < 0) {
                        abnormalIps.add(ips.get(i).toString());
                    }
                }
            }

            log.debug("Abnormal IPs are: {}", abnormalIps.toString());

            dto.setAbnormalIps(abnormalIps);
            // Store fresh result and set last update timestamp
            lastAbnormalIps = new ArrayList<String>(abnormalIps);
            lastUpdateSecondsFromEpoch = nowSecondsFromEpoch;

            return dto;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return dto;
    }

    /**
     * Internal Function that gets the timestamp from elastic and transforms it in a human readable
     * format to show to the frontend
     *
     * @param orig - original string
     * @return String - returned formatted string
     */
    private String getFormattedString(String orig) {

        String retStr = "";
        try {
            SimpleDateFormat sdfIN = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdfIN.setTimeZone(TimeZone.getTimeZone("UTC")); // elastic always returns in UTC
            SimpleDateFormat sdfOUT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Date date = sdfIN.parse(orig);
            retStr = sdfOUT.format(date);

        } catch (ParseException e) {
            log.error("Could not parse string: {}", orig);
            return retStr;
        }
        return retStr;
    }
}

