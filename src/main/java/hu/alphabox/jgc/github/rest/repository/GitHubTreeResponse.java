package hu.alphabox.jgc.github.rest.repository;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
record GitHubTreeResponse(
    String sha,
    String url,
    List<Entry> tree,
    boolean truncated
) {

  @JsonNaming(SnakeCaseStrategy.class)
  record Entry(
      String path,
      @Nullable
      String mode,
      @Nullable
      String type,
      @Nullable
      String sha,
      @Nullable
      Long size,
      @Nullable
      String url
  ) {

  }
}
