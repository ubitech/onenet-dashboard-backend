package eu.ubitech.onenet.model;

import lombok.Data;

@Data
public class UserRequestsInfo {
    private String ipAddress;
    private int http_code;
    private String uri;
    private String city;
    private String country;
    private String countryIso;
    private Double latitude;
    private Double longitude;

    @Override
    public String toString() {
        return  "ipAddress=" + ipAddress +
                ", http_code=" + http_code +
                ", uri=" + uri+
                ", city=" + city +
                ", country=" + country +
                ", countryIso=" + countryIso +
                ", latitude=" + latitude +
                ", longitude=" + longitude
                ;
    }
}