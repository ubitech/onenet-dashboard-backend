package eu.ubitech.onenet.dto;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class AlertDto {
    @Getter
    @Setter
    List<String> abnormalIps;
}

