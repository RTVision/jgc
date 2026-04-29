package hu.alphabox.jgc.github.graphql;

import hu.alphabox.jgc.config.LogTopic;
import hu.alphabox.jgc.gitea.AllowedRepositoryService;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.Reference;
import hu.alphabox.jgc.github.graphql.mapper.ReferenceMapper;
import hu.alphabox.jgc.github.graphql.mapper.RepositoryMapper;
import hu.alphabox.jgc.github.graphql.schema.DeploymentConnection;
import hu.alphabox.jgc.github.graphql.schema.DeploymentOrder;
import hu.alphabox.jgc.github.graphql.schema.PageInfo;
import hu.alphabox.jgc.github.graphql.schema.RefConnection;
import hu.alphabox.jgc.github.graphql.schema.Repository;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j(topic = LogTopic.BACKFILL_DATA_FETCHING)
@Controller
@RequiredArgsConstructor
class RepositoryController {

  private final GiteaClient giteaClient;
  private final RepositoryMapper mapper;
  private final ReferenceMapper referenceMapper;
  private final AllowedRepositoryService allowedRepositoryService;

  @QueryMapping
  Repository repository(
      @Argument String name,
      @Argument String owner
  ) {
    if (!allowedRepositoryService.isAllowed(owner, name)) {
      return null;
    }

    try {
      ResponseEntity<hu.alphabox.jgc.gitea.client.model.Repository> response = giteaClient.repoGet(owner, name);
      log.info("Fetching repository data for: {}", "%s/%s".formatted(owner, name));
      return mapper.node(response.getBody());
    } catch (HttpClientErrorException e) {
      log.debug("Http client exception thrown with status: {}", e.getStatusCode(), e);
      return null;
    }
  }

  @SchemaMapping(typeName = "Repository", field = "refs")
  RefConnection refs(
      Repository repository,
      @Argument String after,
      @Argument Integer first,
      @Argument String refPrefix
  ) {
    if (!allowedRepositoryService.isAllowed(repository.owner().login(), repository.name())) {
      return null;
    }

    String refType = refPrefix.substring(refPrefix.indexOf('/') + 1);
    int pageSize = Objects.requireNonNullElse(first, 10);
    int page = Integer.parseInt(Objects.requireNonNullElse(after, "1"));

    try {
      ResponseEntity<List<Reference>> response = giteaClient.repoListGitRefs(
          repository.owner().login(),
          repository.name(),
          refType
      );
      List<Reference> references = response.getBody();
      if (references == null) {
        references = Collections.emptyList();
      }

      int fromIndex = Math.max(0, (page - 1) * pageSize);
      if (fromIndex >= references.size()) {
        return new RefConnection(
            Collections.emptyList(),
            Collections.emptyList(),
            new PageInfo(
                String.valueOf(page),
                null,
                false,
                page > 1
            ),
            references.size()
        );
      }

      int toIndex = Math.min(references.size(), fromIndex + pageSize);
      String nextCursor = toIndex < references.size() ? String.valueOf(page + 1) : null;
      List<Reference> pagedReferences = references.subList(fromIndex, toIndex);

      log.info(
          "Fetching repository references for: {}\nCurrent page: {}, Page size: {}, Total refs: {}",
          "%s/%s".formatted(repository.owner().login(), repository.name()),
          page,
          pageSize,
          references.size()
      );
      return referenceMapper.map(pagedReferences, repository, pageSize, nextCursor);
    } catch (HttpClientErrorException.NotFound _) {
      return null;
    }
  }

  @SchemaMapping(typeName = "Repository", field = "deployments")
  DeploymentConnection deployments(
      Repository repository,
      @Argument String after,
      @Argument Integer first,
      @Argument DeploymentOrder orderBy
  ) {
    // Implement, but we don't use them, so we return with an empty response
    log.info("Should fetch deployments for repository {}/{} with order: {}",
        repository.name(),
        repository.owner().login(),
        orderBy
    );
    return DeploymentConnection.empty();
  }

}
