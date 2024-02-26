package eu.ubitech.onenet.model;

import lombok.Data;

@Data
public class SecurityReportHitsCount {
    private String ip;
    private String countryCode;
    private Long hitsCount;
    private Long errorsCount;

    public SecurityReportHitsCount(String ip, String countryCode, Long hitsCount, Long errorsCount) {
        this.ip = ip;
        this.countryCode = countryCode;
        this.hitsCount = hitsCount;
        this.errorsCount = errorsCount;
    }
}
