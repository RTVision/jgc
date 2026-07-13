package hu.alphabox.jgc.github.rest.repository;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubPullRequestFileResponse(
    @Nullable
    String sha,
    String filename,
    @Nullable
    String status,
    @Nullable
    Long additions,
    @Nullable
    Long deletions,
    @Nullable
    Long changes,
    @Nullable
    String rawUrl,
    @Nullable
    String contentsUrl,
    @Nullable
    String previousFilename,
    @Nullable
    String patch
) {

}
