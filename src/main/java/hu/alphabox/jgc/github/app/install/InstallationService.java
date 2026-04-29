package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Organization;
import hu.alphabox.jgc.github.app.GitHubAppInstallation;
import hu.alphabox.jgc.github.app.GitHubAppInstallationManager;
import hu.alphabox.jgc.github.app.GitHubAppInstallationMissingException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
@RequiredArgsConstructor
public class InstallationService {

  private static final String CLIENT_ID_PREFIX = "Iv1.";

  private final GiteaClient giteaClient;
  private final GitHubAppInstallationManager appInstallationManager;

  String generateSecret() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[20];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  String generateClientId() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[8];
    random.nextBytes(bytes);
    return CLIENT_ID_PREFIX + HexFormat.of().formatHex(bytes);
  }

  List<Organization> getOrganizationsForUser() {
    List<Organization> organizations = new ArrayList<>();
    int limit = 50;
    int page = 1;
    int totalCount;

    try {
      do {
        ResponseEntity<List<Organization>> response = giteaClient.orgListCurrentUserOrgs(page, limit);
        if (!response.hasBody()) {
          break;
        }
        totalCount = Integer.parseInt(Objects.requireNonNullElse(response.getHeaders().get("X-Total-Count"), List.of("0")).getFirst());
        organizations.addAll(response.getBody());
        page++;
      } while (organizations.size() < totalCount);

      GitHubAppInstallation appInstallation = appInstallationManager.getAppInstallation().orElseThrow(GitHubAppInstallationMissingException::new);
      Set<Long> managedOrganizationIds = appInstallation.getManagedOrganizationIds();

      return organizations.stream()
          .filter(organization -> !managedOrganizationIds.contains(organization.id()))
          .toList();
    } catch (HttpClientErrorException.NotFound _) {
      return Collections.emptyList();
    }
  }

  public Optional<Organization> findOrganizationForUser(Long organizationId) {
    int limit = 50;
    int page = 1;
    int totalCount;
    int fetchedSize = 0;

    try {
      do {
        ResponseEntity<List<Organization>> response = giteaClient.orgListCurrentUserOrgs(page, limit);
        if (!response.hasBody()) {
          break;
        }
        totalCount = Integer.parseInt(Objects.requireNonNullElse(response.getHeaders().get("X-Total-Count"), List.of("0")).getFirst());

        List<Organization> organizations = response.getBody();
        fetchedSize += organizations.size();
        Optional<Organization> organizationOptional = organizations.stream()
            .filter(organization -> organization.id().equals(organizationId))
            .findFirst();

        if (organizationOptional.isPresent()) {
          return organizationOptional;
        }
        page++;
      } while (fetchedSize < totalCount);
      return Optional.empty();
    } catch (HttpClientErrorException.NotFound _) {
      return Optional.empty();
    }
  }
}
