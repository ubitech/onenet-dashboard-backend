package eu.ubitech.onenet.dto;

import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class HttpTransactionsDto {

    @Getter
    @Setter
    List<Long> yaxis;

    @Getter
    @Setter
    List<String> xaxis;
}
