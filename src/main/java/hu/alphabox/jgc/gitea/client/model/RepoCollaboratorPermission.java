package hu.alphabox.jgc.gitea.client.model;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(SnakeCaseStrategy.class)
public record RepoCollaboratorPermission(
    @Nullable
    String permission,
    @Nullable
    String roleName,
    @Nullable
    User user
) {

}
