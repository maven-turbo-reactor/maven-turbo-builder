package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.getProperty;
import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.isTrue;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build start / finish interceptor that prints a warning to avoid confusion.
 *
 * @author Sergey Chernov
 */
@SessionScoped
@Named
public class TurboMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private static final Logger logger = LoggerFactory.getLogger(TurboMavenLifecycleParticipant.class);

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";

    private final DefaultLifecycles lifecycles;

    @Inject
    public TurboMavenLifecycleParticipant(DefaultLifecycles lifecycles) {
        this.lifecycles = lifecycles;
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        if (isTurboBuilder(session)) {
            checkTestJarArtifacts(session);
            checkBuilderAndPhase(session);
        }
    }

    @Override
    public void afterSessionEnd(MavenSession session) {
        if (isTurboBuilder(session)) {
            checkBuilderAndPhase(session);
        }
    }

    private void checkTestJarArtifacts(MavenSession session) throws MavenExecutionException {
        TurboBuilderConfig config = TurboBuilderConfig.fromSession(session);
        if (!config.isTurboTestCompile()) {
            // test-jar is not supported, because package phase is now executed before compiling tests
            for (MavenProject project : session.getProjects()) {
                List<Plugin> jarPlugins = project.getBuildPlugins().stream()
                    .filter(plugin ->
                        "org.apache.maven.plugins".equals(plugin.getGroupId())
                            && "maven-jar-plugin".equals(plugin.getArtifactId()))
                    .collect(Collectors.toList());
                for (Plugin jarPlugin : jarPlugins) {
                    for (PluginExecution pluginExecution : jarPlugin.getExecutions()) {
                        if (hasTestJarGoal(pluginExecution)) {
                            throw new MavenExecutionException("Maven started with turbo builder (`-b turbo` CLI "
                                + "parameter or `-bturbo` in .mvn/maven.config) and it's not compatible with " + project
                                + " test-jar project artifacts and dependencies because of build phase reordering "
                                + "(package phase is now executed before compiling tests). The maven-jar-plugin "
                                + "configuration of the project has configured `test-jar` goal.\n"
                                + "This can be solved in several ways:\n"
                                + "1. Get rid of test-jar packaging if possible\n"
                                + "2. Disable test-jar goal execution when turbo builder is enabled\n"
                                + "3. Opt-in support of test-jar packaging via `-DturboTestCompile` CLI parameter "
                                + "or specified in .mvn/maven.config on a separate line",
                                project.getFile());
                        }
                    }
                }
            }
        }
    }

    private void checkBuilderAndPhase(MavenSession session) {
        TurboBuilderConfig config = TurboBuilderConfig.fromSession(session);
        // skip both compiling and running tests
        boolean mavenTestSkip = isTrue(getProperty(session, "maven.test.skip"));
        // skip only running tests
        boolean skipTests = isTrue(getProperty(session, "skipTests"));
        String skippedReorderedPhases;
        if (mavenTestSkip) {
            // If there is `-Dmaven.test.skip`, don't bother with warning
            skippedReorderedPhases = null;
        } else {
            if (config.isTurboTestCompile()) {
                skippedReorderedPhases = skipTests ? null : "running";
            } else {
                skippedReorderedPhases = skipTests ? "compiling" : "compiling and running";
            }
        }
        if (skippedReorderedPhases != null && session.getRequest().getGoals().contains("package")) {
            logger.warn("package phase is requested in combination with turbo builder (`-bturbo` parameter "
                    + "in the command line or .mvn/maven.config). Please note, that\n"
                    + ANSI_RED + "{} tests is not included in the execution" + ANSI_RESET
                    + " because of phase reordering.\n"
                    + "{}To run tests, use `test`, `verify` or `install` phase instead of `package`.",
                skippedReorderedPhases,
                config.isTurboTestCompile() ? "" : "To compile tests, run with parameter `-DturboTestCompile`.\n");
        }
    }

    private boolean hasTestJarGoal(PluginExecution pluginExecution) {
        // as well as checking for the test-jar goal also check it's bound to a phase
        // that actually exists as a common pattern to disable the goal when it's
        // defined in a parent pom is to use a goal like 'none' or 'never'
        return pluginExecution.getGoals().contains("test-jar") &&
                lifecycles.getPhaseToLifecycleMap().get(pluginExecution.getPhase()) != null;
    }

    private static boolean isTurboBuilder(MavenSession session) {
        String builderId = session.getRequest().getBuilderId();
        return TurboBuilder.BUILDER_TURBO.equals(builderId);
    }
}
