package app.saikiran.SentinelFlow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SentinelFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(SentinelFlowApplication.class, args);
	}

}
