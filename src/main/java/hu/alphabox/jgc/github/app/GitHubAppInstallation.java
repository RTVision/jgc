package hu.alphabox.jgc.github.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@RequiredArgsConstructor
@Getter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GitHubAppInstallation {

  private static final String DEFAULT_NAME = "Gitea";

  @EqualsAndHashCode.Include
  private final Long id;
  private final String clientId;
  private final String clientSecret;
  private final String webhookUrl;
  private final String callbackUrl;
  private final String setupUrl;
  private final String privateKey;
  private final String publicKey;
  private final List<String> defaultEvents;
  private final Map<String, String> defaultPermissions;
  private final Set<Installation> installations;
  private final Long ownerId;
  // app name from the registration manifest; installations registered before it was persisted have none
  private final String name;
  @Setter
  private String webhookSecret;

  @RequiredArgsConstructor
  @Getter
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class Installation {

    @EqualsAndHashCode.Include
    private final Long id;
    @Setter
    private Long organizationId;
    @Setter
    private Set<String> selectedRepositories = new LinkedHashSet<>();

    @JsonIgnore
    public boolean isConfigured() {
      return organizationId != null;
    }

    @JsonIgnore
    public boolean hasSelectedRepositories() {
      return selectedRepositories != null && !selectedRepositories.isEmpty();
    }
  }

  public String getName() {
    return name != null ? name : DEFAULT_NAME;
  }

  // GitHub derives the app slug from its name; integrations (e.g. Drata) check it
  @JsonIgnore
  public String getSlug() {
    return getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  public Optional<Installation> getInstallation(Long id) {
    return installations.stream().filter(i -> id.equals(i.getId())).findFirst();
  }

  public Installation resolveInstallationForConfiguration() {
    return installations.stream()
        .filter(Predicate.not(Installation::isConfigured))
        .findFirst()
        .orElseGet(() -> new Installation(
            installations.stream()
                .mapToLong(Installation::getId)
                .max().
                orElse(0L) + 1
        ));
  }

  public Set<Installation> getInstallations() {
    return Set.copyOf(installations);
  }

  public void addInstallation(Installation installation) {
    installations.add(installation);
  }

  @JsonIgnore
  public Set<Long> getManagedOrganizationIds() {
    return installations.stream()
        .map(Installation::getOrganizationId)
        .collect(Collectors.toSet());
  }
}
