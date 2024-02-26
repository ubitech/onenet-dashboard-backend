package eu.ubitech.onenet.model;

import java.time.Instant;
import javax.persistence.Id;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "connectors-*", createIndex = false)
public class ConnectorLogs {

    @Id
    private String id;

    @Field(type = FieldType.Date, name = "@timestamp")
    private String timestamp;

    @Field(type = FieldType.Text, name = "message")
    private String message;

    @Field(type = FieldType.Auto, name = "agent")
    private Agent agent = new Agent();

    @Field(type = FieldType.Auto, name = "client_geoip")
    public Geoip geoip = new Geoip();

    private class Agent{
        @Field(type = FieldType.Text, name = "id")
        private String id;
    }

    public class Geoip{
        @Field(type = FieldType.Text, name = "country_name")
        public String countryName;
        @Field(type = FieldType.Text, name = "country_code2")
        public String countryIso;
    }
}
