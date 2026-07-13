package hu.alphabox.jgc.gitea.client.model;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record ChangedFile(
    @Nullable
    String filename,
    @Nullable
    String previousFilename,
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
    String htmlPatchUrl,
    @Nullable
    String patch
) {

}
