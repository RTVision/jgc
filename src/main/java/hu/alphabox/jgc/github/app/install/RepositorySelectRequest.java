package hu.alphabox.jgc.github.app.install;

import java.util.List;

record RepositorySelectRequest(
    List<String> repositories
) {
}
