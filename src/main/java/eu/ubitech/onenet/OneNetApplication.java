package eu.ubitech.onenet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@Slf4j
@SpringBootApplication
public class OneNetApplication {

	public static void main(String[] args) {
		log.info("OneNet, is starting");
		SpringApplication.run(OneNetApplication.class, args);
	}

}
