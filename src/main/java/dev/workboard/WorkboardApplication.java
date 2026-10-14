package dev.workboard;

import dev.workboard.infrastructure.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class WorkboardApplication {
  public static void main(String[] args) {
    SpringApplication.run(WorkboardApplication.class, args);
  }
}
