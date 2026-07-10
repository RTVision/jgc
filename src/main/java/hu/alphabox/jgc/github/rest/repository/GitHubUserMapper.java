package hu.alphabox.jgc.github.rest.repository;

import hu.alphabox.jgc.TypeConversionMapper;
import hu.alphabox.jgc.config.MapperCentralConfig;
import hu.alphabox.jgc.gitea.client.GiteaClient;
import hu.alphabox.jgc.gitea.client.model.User;
import java.util.List;
import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

@Mapper(
    config = MapperCentralConfig.class,
    uses = TypeConversionMapper.class
)
public abstract class GitHubUserMapper {

  @Setter(onMethod_ = @Autowired)
  private GiteaClient giteaClient;

  @Mapping(target = "name", source = "fullName")
  @Mapping(target = "siteAdmin", source = "isAdmin")
  @Mapping(target = "createdAt", source = "created")
  @Mapping(target = "type", source = "login", qualifiedByName = "type")
  public abstract GitHubUserResponse map(User user);

  public abstract List<GitHubUserResponse> map(List<User> users);

  @Named("type")
  protected String type(String login) {
    try {
      giteaClient.orgGet(login);
      return "Organization";
    } catch (HttpClientErrorException _) {
      return "User";
    }
  }

}
