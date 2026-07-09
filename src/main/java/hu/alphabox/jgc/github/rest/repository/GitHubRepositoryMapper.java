package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.TypeConversionMapper;
import hu.alphabox.jgc.config.MapperCentralConfig;
import hu.alphabox.jgc.gitea.client.model.ContentsResponse;
import hu.alphabox.jgc.gitea.client.model.Repository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    config = MapperCentralConfig.class,
    uses = TypeConversionMapper.class
)
public interface GitHubRepositoryMapper {

  @Mapping(target = "openIssues", source = "openIssuesCount")
  @Mapping(target = "isTemplate", source = "template")
  GitHubRepositoryResponse repository(Repository repository);

  GitHubContentTreeResponse content(ContentsResponse response);
}
