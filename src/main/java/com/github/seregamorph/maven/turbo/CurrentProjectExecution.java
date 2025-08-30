package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.getProperty;
import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.isTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;

/**
 * @author Sergey Chernov
 */
final class CurrentProjectExecution {

    private static final ThreadLocal<CurrentProjectExecution> currentProjectExecution = new ThreadLocal<>();

    final List<MojoExecution> executedPackageMojos = new ArrayList<>();

    boolean signaled;

    List<MojoExecution> packageMojos;

    private CurrentProjectExecution(MavenSession session, MavenProject project) {
        // There can be scenarios when we use TurboBuilder as default, but disable per project, property or via profile,
        // when it's known that the downstream dependencies should be only scheduled when all phases are completed.
        signaled = isTrue(getProperty(session, project, "skipTurboSignal"));
    }

    static void doWithCurrentProject(MavenSession session, MavenProject project, Runnable task) {
        CurrentProjectExecution execution = new CurrentProjectExecution(session, project);
        currentProjectExecution.set(execution);
        try {
            task.run();
        } finally {
            currentProjectExecution.remove();
        }
    }

    static void ifPresent(Consumer<CurrentProjectExecution> action) {
        CurrentProjectExecution execution = currentProjectExecution.get();
        if (execution != null) {
            action.accept(execution);
        }
    }
}
