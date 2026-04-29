package hu.alphabox.jgc.github.graphql;

import hu.alphabox.jgc.LinkHeaderProcessor.LinkManager;
import hu.alphabox.jgc.config.LogTopic;
import hu.alphabox.jgc.gitea.GiteaPageResponse;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Commit;
import hu.alphabox.jgc.github.graphql.mapper.PullRequestMapper;
import hu.alphabox.jgc.github.graphql.mapper.PullRequestMapper.PullRequestCommitMapper;
import hu.alphabox.jgc.github.graphql.schema.IssueOrder;
import hu.alphabox.jgc.github.graphql.schema.PullRequest;
import hu.alphabox.jgc.github.graphql.schema.PullRequestCommitConnection;
import hu.alphabox.jgc.github.graphql.schema.PullRequestConnection;
import hu.alphabox.jgc.github.graphql.schema.PullRequestReviewConnection;
import hu.alphabox.jgc.github.graphql.schema.PullRequestState;
import hu.alphabox.jgc.github.graphql.schema.Repository;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j(topic = LogTopic.BACKFILL_DATA_FETCHING)
@Controller
@RequiredArgsConstructor
class PullRequestController {

  private static final int MAX_PULL_REQUESTS = 10;
  private static final int MAX_PULL_REQUEST_COMMITS = 1;

  private final GiteaClient giteaClient;
  private final PullRequestMapper mapper;
  private final PullRequestCommitMapper commitMapper;

  @SchemaMapping(typeName = "Repository", field = "pullRequest")
  PullRequest pullRequest(
      Repository repository,
      @Argument Long number
  ) {
    try {
      ResponseEntity<hu.alphabox.jgc.gitea.client.model.PullRequest> response =
          giteaClient.repoGetPullRequest(repository.owner().login(), repository.name(), number);

      log.info(
          "Fetching pull request data for: {} with number: {}",
          "%s/%s".formatted(repository.owner().login(), repository.name()),
          number
      );
      return mapper.node(response.getBody(), repository);
    } catch (HttpClientErrorException.NotFound _) {
      return null;
    }
  }

  @SchemaMapping(typeName = "Repository", field = "pullRequests")
  PullRequestConnection pullRequests(
      Repository repository,
      @Argument String after,
      @Argument Integer first,
      @Argument IssueOrder orderBy
  ) {
    int page = Integer.parseInt(Objects.requireNonNullElse(after, "1"));
    int limit = Math.min(Objects.requireNonNullElse(first, MAX_PULL_REQUESTS), MAX_PULL_REQUESTS);
    try {
      GiteaPageResponse<hu.alphabox.jgc.gitea.client.model.PullRequest> response = GiteaPageResponse.of(giteaClient.repoListPullRequests(
          repository.owner().login(),
          repository.name(),
          null,
          "all",
          null,
          null,
          null,
          null,
          page,
          limit
      ));

      log.info(
          "Fetching pull requests for: {}\nCurrent page: {}, Total pull request count: {}",
          "%s/%s".formatted(repository.owner().login(), repository.name()),
          page,
          response.totalCount()
      );

      return mapper.map(after, response, repository);
    } catch (HttpClientErrorException.NotFound _) {
      return mapper.map(after, new GiteaPageResponse<>(Collections.emptyList(), LinkManager.empty(), 0), repository);
    }
  }

  @SchemaMapping(typeName = "PullRequest", field = "reviews")
  PullRequestReviewConnection reviews(
      PullRequest pullRequest,
      @Argument String after,
      @Argument Integer first
  ) {
    return new PullRequestReviewConnection(
        Collections.emptyList(),
        Collections.emptyList(),
        hu.alphabox.jgc.github.graphql.schema.PageInfo.empty(),
        0
    );
  }

  // FIXME The logged GraphQL queries are using backward pagination, but for some reason, we check the forward pagination arguments
  @SchemaMapping(typeName = "PullRequest", field = "commits")
  PullRequestCommitConnection commits(
      PullRequest pullRequest,
      @Argument String after,
      @Argument String before,
      @Argument Integer first,
      @Argument Integer last
  ) {
    int page = Integer.parseInt(Objects.requireNonNullElse(after, "1"));
    int limit = Math.min(Objects.requireNonNullElse(first, MAX_PULL_REQUEST_COMMITS), MAX_PULL_REQUEST_COMMITS);

    // as we don't get the changed files number from pull request listing, and get an error for PR commits if the PR branches missing,
    // we have to get PR base information to check anything changed in PR
    // This only happens on closed PRs
    if (PullRequestState.CLOSED == pullRequest.getState()) {
      ResponseEntity<hu.alphabox.jgc.gitea.client.model.PullRequest> prFromGitea = giteaClient.repoGetPullRequest(
          pullRequest.getRepository().owner().login(),
          pullRequest.getRepository().name(),
          Long.valueOf(pullRequest.getNumber())
      );

      // if PR base ref is missing (deleted), the PR commits API endpoint returns with HTTP 500
      // see: https://github.com/go-gitea/gitea/issues/36366
      if (!StringUtils.hasText(prFromGitea.getBody().base().sha())) {
        return commitMapper.map(String.valueOf(page), GiteaPageResponse.empty(), pullRequest.getRepository());
      }

      if (prFromGitea.getBody().changedFiles() == 0) {
        return commitMapper.map(String.valueOf(page), GiteaPageResponse.empty(), pullRequest.getRepository());
      }
    }

    GiteaPageResponse<Commit> response = GiteaPageResponse.of(giteaClient.repoGetPullRequestCommits(
        pullRequest.getRepository().owner().login(),
        pullRequest.getRepository().name(),
        Long.valueOf(pullRequest.getNumber()),
        page,
        limit,
        false,
        false
    ));

    log.info(
        "Fetching pull request commits for: {} with PR number: {}\nCurrent page: {}, Total pull request commit count: {}",
        "%s/%s".formatted(pullRequest.getRepository().owner().login(), pullRequest.getRepository().name()),
        pullRequest.getNumber(),
        page,
        response.totalCount()
    );

    return commitMapper.map(String.valueOf(page), response, pullRequest.getRepository());
  }

}
