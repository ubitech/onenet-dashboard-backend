package eu.ubitech.onenet.dto;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdvancedFilteringDto {

    public String connector;
    public String dateFrom;
    public String dateTo;
    public String bytesSentMin;
    public String bytesSentMax;
    public List<String> clientIPs = new ArrayList<>();
    public List<String> requestMethods = new ArrayList<>();
    public List<String> responseCodes = new ArrayList<>();
    public List<String> countries = new ArrayList<>();

    public String toString() {
        return  "connector = " + connector +
                ", dateFrom = " + dateFrom +
                ", dateTo = " + dateTo+
                ", bytesSentMin = " + bytesSentMin +
                ", bytesSentMax = " + bytesSentMax +
                ", clientIPs = " + clientIPs +
                ", requestMethods = " + requestMethods +
                ", responseCodes = " + responseCodes +
                ", countries = " + countries
                ;
    }
}
