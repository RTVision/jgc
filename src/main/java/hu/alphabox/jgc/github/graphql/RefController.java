package hu.alphabox.jgc.github.graphql;

import hu.alphabox.jgc.config.LogTopic;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.github.graphql.mapper.CommitMapper;
import hu.alphabox.jgc.github.graphql.schema.Commit;
import hu.alphabox.jgc.github.graphql.schema.GitObject;
import hu.alphabox.jgc.github.graphql.schema.PullRequestConnection;
import hu.alphabox.jgc.github.graphql.schema.Ref;
import hu.alphabox.jgc.github.graphql.schema.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpClientErrorException;

@Controller
@RequiredArgsConstructor
@Slf4j(topic = LogTopic.BACKFILL_DATA_FETCHING)
class RefController {

  private final GiteaClient giteaClient;
  private final CommitMapper commitMapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @SchemaMapping(typeName = "Ref", field = "associatedPullRequests")
  PullRequestConnection associatedPullRequests(
      @Argument String after,
      @Argument Integer first
  ) {
    // TODO :: Cannot handle this request at the moment
    return PullRequestConnection.empty();
  }


  @SchemaMapping(typeName = "Ref", field = "target")
  GitObject target(Ref ref) {
    if (!allowedRepositoryService.isAllowed(ref.getRepository().owner().login(), ref.getRepository().name())) {
      return null;
    }

    if (ref.getTarget() != null) {
      return ref.getTarget();
    }

    log.info("Fetching reference data for: {}{}", ref.getPrefix(), ref.getName());
    return switch (ref.getPrefix()) {
      case "refs/heads/" -> commit(ref);
      case "refs/tags/" -> tag(ref.getTarget().oid());
      default -> throw new IllegalArgumentException("Unhandled ref type:  " + ref.getPrefix());
    };
  }

  private Commit commit(Ref ref) {
    try {
      ResponseEntity<hu.alphabox.jgc.gitea.client.model.Commit> response = giteaClient.repoGetSingleCommit(
          ref.getRepository().owner().login(),
          ref.getRepository().name(),
          ref.getName(),
          false,
          false,
          true
      );
      hu.alphabox.jgc.gitea.client.model.Commit giteaCommit = response.getBody();
      log.info(
          "Fetching last commit data for: {} with ref: {}{}\nReturned commit: {}",
          "%s/%s".formatted(ref.getRepository().owner().login(), ref.getRepository().name()),
          ref.getPrefix(), ref.getName(),
          giteaCommit.sha()
      );
      return commitMapper.node(giteaCommit, ref.getRepository());
    } catch (HttpClientErrorException e) {
      log.debug("Http client exception thrown with status: {}", e.getStatusCode(), e);
      return null;
    }
  }

  private Tag tag(String oid) {
    return new Tag(oid);
  }

}
