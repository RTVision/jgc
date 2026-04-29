package hu.alphabox.jgc.gitea;

import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AllowedRepositoryService {

  private final Set<String> allowedRepositories;

  public AllowedRepositoryService(GiteaConfigurationProperties properties) {
    this.allowedRepositories = Set.copyOf(properties.allowedRepositories());
  }

  public boolean isAllowed(String owner, String repository) {
    return isAllowed("%s/%s".formatted(owner, repository));
  }

  public boolean isAllowed(String repositoryFullName) {
    return allowedRepositories.isEmpty() || allowedRepositories.contains(repositoryFullName);
  }
}
