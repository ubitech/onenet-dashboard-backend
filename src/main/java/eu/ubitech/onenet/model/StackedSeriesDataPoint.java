package eu.ubitech.onenet.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class StackedSeriesDataPoint {

    @Getter
    @Setter
    String category;

    @Getter
    @Setter
    String name;

    @Getter
    @Setter
    Long dataPoint;

}
