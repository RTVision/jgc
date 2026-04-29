package hu.alphabox.jgc;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("application")
public record ApplicationProperties(
    @NotBlank
    String baseUrl,
    @NotBlank
    String installationPath,
    GraphQlProperties graphQl
) {

  public record GraphQlProperties(
      Duration timeout
  ) {

    public GraphQlProperties {
      if (timeout == null) {
        timeout = Duration.ofSeconds(60);
      }
    }

  }

}
