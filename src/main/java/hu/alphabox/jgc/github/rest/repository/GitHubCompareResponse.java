package hu.alphabox.jgc.github.rest.repository;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubCompareResponse(
    long totalCommits,
    List<GitHubCommitResponse> commits
) {

}
