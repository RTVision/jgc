package hu.alphabox.jgc.github.rest.repository;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubPullRequestResponse(
    Long id,
    Long number,
    String state,
    Boolean draft,
    Boolean merged,
    String title,
    String body,
    GitHubPullRequestReviewResponse.SimpleUser user,
    List<GitHubPullRequestReviewResponse.SimpleUser> requestedReviewers,
    URI htmlUrl,
    String mergeCommitSha,
    OffsetDateTime mergedAt,
    OffsetDateTime closedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    Branch base,
    Branch head
) {

  @JsonNaming(SnakeCaseStrategy.class)
  record Branch(
      String label,
      String ref,
      String sha,
      Repo repo
  ) {

  }

  @JsonNaming(SnakeCaseStrategy.class)
  record Repo(
      Long id,
      String name,
      String fullName
  ) {

  }
}
