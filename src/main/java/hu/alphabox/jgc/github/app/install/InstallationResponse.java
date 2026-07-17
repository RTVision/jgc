package hu.alphabox.jgc.github.app.install;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record InstallationResponse(
    Long id,
    String clientId,
    AccountResponse account,
    RepositorySelection repositorySelection,
    String accessTokensUrl,
    String repositoriesUrl,
    String htmlUrl,
    Long appId,
    String appSlug,
    Long targetId,
    ActorType targetType,
    Map<String, String> permissions,
    List<String> events,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant updatedAt,
    String singleFileName,
    Boolean hasMultipleSingleFiles,
    List<String> singleFilePaths,
    String suspendedBy,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant suspendedAt
) {

  record AccountResponse(
      Long id,
      String login,
      String avatarUrl,
      String htmlUrl,
      ActorType type,
      Boolean siteAdmin
  ) {

  }

}
