package hu.alphabox.jgc.github.app.install;

import hu.alphabox.jgc.github.rest.repository.GitHubRepositoryResponse;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record InstallationRepositoriesResponse(
    Integer totalCount,
    RepositorySelection repositorySelection,
    List<GitHubRepositoryResponse> repositories
) {
}
