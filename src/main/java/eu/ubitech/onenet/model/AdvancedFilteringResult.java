package eu.ubitech.onenet.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "connectors-*", createIndex = false)
public class AdvancedFilteringResult {
    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "@timestamp")
    String timestamp;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "headers.x_forwarded_for")
    String connector;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "verb")
    String requestMethod;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "request")
    String path;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "headers.content_length")
    String contentLength;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "response")
    String responseCode;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "bytes")
    String bytesSent;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "client_geoip.ip")
    String clientIP;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "user_agent.os")
    String os;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "user_agent.name")
    String browser;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "client_geoip.country_code2")
    String countryCode;

    @Getter
    @Setter
    @Field(type = FieldType.Text, name = "client_geoip.city_name")
    String cityName;

    public AdvancedFilteringResult(
            String timestamp,
            String connector,
            String requestMethod,
            String path,
            String contentLength,
            String responseCode,
            String bytesSent,
            String clientIP,
            String os,
            String browser,
            String countryCode,
            String cityName) {
        this.timestamp = timestamp;
        this.connector = connector;
        this.requestMethod = requestMethod;
        this.path = path;
        this.contentLength = contentLength;
        this.responseCode = responseCode;
        this.bytesSent = bytesSent;
        this.clientIP = clientIP;
        this.os = os;
        this.browser = browser;
        this.countryCode = countryCode;
        this.cityName = cityName;
    }

    public String toString() {
        return "timestamp = "+timestamp+
            ", connector = "+connector+
            ", requestMethod = "+requestMethod+
            ", path = "+path+
            ", contentLength = "+contentLength+
            ", responseCode = "+responseCode+
            ", bytesSent = "+bytesSent+
            ", clientIP = "+clientIP+
            ", os = "+os+
            ", browser = "+browser+
            ", countryCode = "+countryCode+
            ", cityName = "+cityName;
    }
}
