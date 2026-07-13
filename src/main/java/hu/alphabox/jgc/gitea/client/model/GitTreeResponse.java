package hu.alphabox.jgc.gitea.client.model;

import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record GitTreeResponse(
    @Nullable
    String sha,
    @Nullable
    String url,
    @Nullable
    List<Entry> tree,
    @Nullable
    Boolean truncated,
    @Nullable
    Integer page,
    @Nullable
    Integer totalCount
) {

  @JsonNaming(SnakeCaseStrategy.class)
  public record Entry(
      @Nullable
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
