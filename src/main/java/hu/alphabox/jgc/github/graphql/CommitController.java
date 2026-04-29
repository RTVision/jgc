package hu.alphabox.jgc.github.graphql;

import hu.alphabox.jgc.config.LogTopic;
import hu.alphabox.jgc.gitea.GiteaPageResponse;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.github.graphql.mapper.CommitMapper.CommitHistoryMapper;
import hu.alphabox.jgc.github.graphql.mapper.GitActorMapper;
import hu.alphabox.jgc.github.graphql.schema.Commit;
import hu.alphabox.jgc.github.graphql.schema.CommitHistoryConnection;
import hu.alphabox.jgc.github.graphql.schema.GitActor;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpClientErrorException;

@Controller
@Slf4j(topic = LogTopic.BACKFILL_DATA_FETCHING)
@RequiredArgsConstructor
class CommitController {

  private final GiteaClient giteaClient;
  private final GitActorMapper actorMapper;
  private final CommitHistoryMapper historyMapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @SchemaMapping(typeName = "Commit", field = "history")
  CommitHistoryConnection handle(
      Commit commit,
      @Argument String after,
      @Argument Integer first,
      @Argument Instant since
  ) {
    if (!allowedRepositoryService.isAllowed(commit.repository().owner().login(), commit.repository().name())) {
      String cursor = Objects.requireNonNullElse(after, "1");
      return historyMapper.map(cursor, GiteaPageResponse.empty(), commit.repository());
    }

    int startCursor = Integer.parseInt(Objects.requireNonNullElse(after, "1"));
    GiteaPageResponse<hu.alphabox.jgc.gitea.client.model.Commit> response = GiteaPageResponse.of(giteaClient.repoGetAllCommits(
        commit.repository().owner().login(),
        commit.repository().name(),
        commit.oid(),
        null,
        false,
        false,
        true,
        startCursor,
        first,
        null
    ));

    log.info(
        "Fetching commit history for: {} with OID: {}\nCurrent page: {}, Total commit count: {}",
        "%s/%s".formatted(commit.repository().owner().login(), commit.repository().name()),
        commit.oid(),
        startCursor,
        response.totalCount()
    );
    return historyMapper.map(String.valueOf(startCursor), response, commit.repository());
  }

  @SchemaMapping(typeName = "Commit", field = "author")
  GitActor author(
      Commit commit
  ) {
    if (!allowedRepositoryService.isAllowed(commit.repository().owner().login(), commit.repository().name())) {
      return null;
    }

    if (commit.author() != null) {
      return commit.author();
    }

    try {
      ResponseEntity<hu.alphabox.jgc.gitea.client.model.Commit> response = giteaClient.repoGetSingleCommit(
          commit.repository().owner().login(),
          commit.repository().name(),
          commit.oid(),
          false,
          false,
          false
      );
      hu.alphabox.jgc.gitea.client.model.Commit responseBody = response.getBody();

      log.info(
          "Fetching commit author for: {} with OID: {}",
          "%s/%s".formatted(commit.repository().owner().login(), commit.repository().name()),
          commit.oid()
      );

      return actorMapper.gitActor(responseBody);
    } catch (HttpClientErrorException e) {
      log.debug("Http client exception thrown with status: {}", e.getStatusCode(), e);
      return null;
    }
  }


}
