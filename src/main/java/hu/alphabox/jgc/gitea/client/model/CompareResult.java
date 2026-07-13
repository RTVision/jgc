package hu.alphabox.jgc.gitea.client.model;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record CompareResult(
    @Nullable
    Long totalCommits,
    @Nullable
    List<Commit> commits
) {

}
