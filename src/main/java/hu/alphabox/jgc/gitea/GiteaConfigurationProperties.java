package hu.alphabox.jgc.gitea;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application.gitea")
@Validated
public record GiteaConfigurationProperties(
    @NotBlank
    String baseUrl,
    List<String> allowedRepositories
) {

  public GiteaConfigurationProperties {
    if (allowedRepositories == null) {
      allowedRepositories = List.of();
    }
  }

}
