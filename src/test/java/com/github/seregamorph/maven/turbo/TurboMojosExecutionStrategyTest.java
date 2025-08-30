package com.github.seregamorph.maven.turbo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.DefaultMojosExecutionStrategy;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionRunner;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

/**
 * @author Sergey Chernov
 */
@SuppressWarnings("CodeBlock2Expr")
class TurboMojosExecutionStrategyTest {

    @Test
    public void shouldReorderAndSignalFullPhasesNoTestJarSupported() throws LifecycleExecutionException {
        List<String> phases = List.of(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",
            // note: already reordered before test phases
            "prepare-package",
            "package",

            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy"
        );
        var expectedEvents = List.of(
            "exec:validate",
            "exec:initialize",
            "exec:generate-sources",
            "exec:process-sources",
            "exec:generate-resources",
            "exec:process-resources",
            "exec:compile",
            "exec:process-classes",
            "exec:prepare-package",
            "exec:package",
            "signal",
            "exec:generate-test-sources",
            "exec:process-test-sources",
            "exec:generate-test-resources",
            "exec:process-test-resources",
            "exec:test-compile",
            "exec:process-test-classes",
            "exec:test",
            "exec:pre-integration-test",
            "exec:integration-test",
            "exec:post-integration-test",
            "exec:verify",
            "exec:install",
            "exec:deploy"
        );
        shouldReorderAndSignalImpl(phases, expectedEvents);
    }

    @Test
    public void shouldReorderAndSignalFullPhasesTestJarSupported() throws LifecycleExecutionException {
        List<String> phases = List.of(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            // note: already reordered before test phases
            "prepare-package",
            "package",
            "test",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy"
        );
        var expectedEvents = List.of(
            "exec:validate",
            "exec:initialize",
            "exec:generate-sources",
            "exec:process-sources",
            "exec:generate-resources",
            "exec:process-resources",
            "exec:compile",
            "exec:process-classes",
            "exec:generate-test-sources",
            "exec:process-test-sources",
            "exec:generate-test-resources",
            "exec:process-test-resources",
            "exec:test-compile",
            "exec:process-test-classes",
            "exec:prepare-package",
            "exec:package",
            "signal",
            "exec:test",
            "exec:pre-integration-test",
            "exec:integration-test",
            "exec:post-integration-test",
            "exec:verify",
            "exec:install",
            "exec:deploy"
        );
        shouldReorderAndSignalImpl(phases, expectedEvents);
    }

    @Test
    public void shouldReorderAndSignalSubsetPhasesNoTestJarSupported() throws LifecycleExecutionException {
        List<String> phases = List.of(
            "validate",
            "initialize",
            "generate-sources",
            "process-sources",
            "generate-resources",
            "process-resources",
            "compile",
            "process-classes",

            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test"
        );
        var expectedEvents = List.of(
            "exec:validate",
            "exec:initialize",
            "exec:generate-sources",
            "exec:process-sources",
            "exec:generate-resources",
            "exec:process-resources",
            "exec:compile",
            "exec:process-classes",
            "signal",
            "exec:generate-test-sources",
            "exec:process-test-sources",
            "exec:generate-test-resources",
            "exec:process-test-resources",
            "exec:test-compile",
            "exec:process-test-classes",
            "exec:test"
        );

        shouldReorderAndSignalImpl(phases, expectedEvents);
    }

    private static void shouldReorderAndSignalImpl(List<String> phases, List<String> expectedEvents) throws LifecycleExecutionException {
        var executionPlan = phases.stream()
            .map(phase -> {
                var execution = new MojoExecution(null);
                execution.setLifecyclePhase(phase);
                return execution;
            })
            .toList();

        var request = new DefaultMavenExecutionRequest();
        var session = new MavenSession(null, null, request, null);
        var project = new MavenProject();
        session.setCurrentProject(project);

        var strategy = new DefaultMojosExecutionStrategy();
        var eventsList = new ArrayList<String>();
        var turboProjectExecutionListener = new TurboProjectExecutionListener();
        var turboMojoExecutionListener = new TurboMojoExecutionListener();
        var mojoRunner = new MojoExecutionRunner() {
            @Override
            public void run(MojoExecution execution) {
                turboMojoExecutionListener.beforeMojoExecution(new MojoExecutionEvent(session, project, execution, null));
                eventsList.add("exec:" + execution.getLifecyclePhase());
                turboMojoExecutionListener.afterMojoExecutionSuccess(new MojoExecutionEvent(session, project, execution, null));
            }
        };

        CurrentProjectExecution.doWithCurrentProject(session, project, () -> {
            turboProjectExecutionListener.beforeProjectLifecycleExecution(
                new ProjectExecutionEvent(session, project, executionPlan));
            SignalingExecutorCompletionService.currentSignaler.set(p -> {
                eventsList.add("signal");
            });
            try {
                strategy.execute(executionPlan, session, mojoRunner);
            } catch (LifecycleExecutionException e) {
                throw new RuntimeException(e);
            } finally {
                SignalingExecutorCompletionService.currentSignaler.remove();
            }
        });

        assertEquals(expectedEvents, eventsList);
    }
}
