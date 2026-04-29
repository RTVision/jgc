package hu.alphabox.jgc.github.graphql;

import hu.alphabox.jgc.ApplicationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.graphql.autoconfigure.ConditionalOnGraphQlSchema;
import org.springframework.boot.graphql.autoconfigure.GraphQlProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.TimeoutWebGraphQlInterceptor;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnGraphQlSchema
@EnableConfigurationProperties(GraphQlProperties.class)
class GraphQLConfiguration {

  private final ApplicationProperties applicationProperties;
  private final GraphQlProperties graphQlProperties;

  @Bean
  TimeoutWebGraphQlInterceptor timeoutWebGraphQlInterceptor() {
    return new TimeoutWebGraphQlInterceptor(applicationProperties.graphQl().timeout());
  }

  @Bean
  FilterRegistrationBean<GraphQLAcceptHeaderFilter> graphQLAcceptHeaderFilter() {
    FilterRegistrationBean<GraphQLAcceptHeaderFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new GraphQLAcceptHeaderFilter());
    registrationBean.addUrlPatterns(graphQlProperties.getHttp().getPath());
    return registrationBean;
  }

}
