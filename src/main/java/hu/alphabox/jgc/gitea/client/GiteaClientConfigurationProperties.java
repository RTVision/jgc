package hu.alphabox.jgc.gitea.client;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application.gitea.client")
@Validated
public record GiteaClientConfigurationProperties(
    @NotBlank
    String accessToken,
    @NotNull
    Integer concurrencyLimit,
    Duration requestDelay
) {

  public GiteaClientConfigurationProperties {
    if (requestDelay == null) {
      requestDelay = Duration.ZERO;
    }
  }

}
