package hu.alphabox.jgc.gitea.client.model;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record Organization(
    @Nullable
    String avatarUrl,
    @Nullable
    String description,
    @Nullable
    Long id,
    @Nullable
    String name
) {

}

