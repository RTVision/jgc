package hu.alphabox.jgc.github.graphql.mapper;

import hu.alphabox.jgc.TypeConversionMapper;
import hu.alphabox.jgc.config.MapperCentralConfig;
import hu.alphabox.jgc.gitea.client.model.Reference;
import hu.alphabox.jgc.github.graphql.schema.PageInfo;
import hu.alphabox.jgc.github.graphql.schema.Ref;
import hu.alphabox.jgc.github.graphql.schema.RefConnection;
import hu.alphabox.jgc.github.graphql.schema.RefEdge;
import hu.alphabox.jgc.github.graphql.schema.Repository;
import java.util.List;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(
    config = MapperCentralConfig.class,
    uses = {
        TypeConversionMapper.class,
        GitActorMapper.class
    }
)
public interface ReferenceMapper {

  default RefConnection map(List<Reference> references, Repository repository, int first, String nextCursor) {
    List<Ref> nodes = nodes(references, repository);
    boolean hasNextPage = nextCursor != null;

    return new RefConnection(
        nodes.stream().map(node -> edge(node, nextCursor)).toList(),
        nodes,
        new PageInfo(
            "1",
            nextCursor,
            hasNextPage,
            false
        ),
        references.size()
    );
  }

  List<Ref> nodes(List<Reference> references, @Context Repository repository);

  @Mapping(target = "repository", expression = "java(repository)")
  @Mapping(target = "name", source = "reference.ref", qualifiedByName = "refName")
  @Mapping(target = "prefix", source = "reference.ref", qualifiedByName = "refPrefix")
  Ref node(Reference reference, @Context Repository repository);

  RefEdge edge(Ref node, String cursor);

  @Named("refName")
  default String refName(String ref) {
    return ref.split("/", 3)[2];
  }

  @Named("refPrefix")
  default String refPrefix(String ref) {
    String[] refParts = ref.split("/", 3);
    return refParts[0] + '/' + refParts[1] + '/';
  }
}
