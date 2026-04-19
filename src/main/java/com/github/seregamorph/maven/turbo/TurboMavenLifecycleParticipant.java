package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.getProperty;
import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.isTrue;

import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.SessionScoped;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
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

    private final Set<String> lifecyclePhases;

    @Inject
    public TurboMavenLifecycleParticipant(DefaultLifecycles lifecycles) {
        // note: calling lifecycles.getPhaseToLifecycleMap() in constructor as for some reason
        // same call from the "afterProjectsRead" may lead with NPE (DefaultLifecycles.lifecycles==null)
        lifecyclePhases = lifecycles.getPhaseToLifecycleMap().keySet();
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        if (TurboBuilder.isTurboBuilder(session)) {
            checkBuilderAndPhase(session);
        }
    }

    @Override
    public void afterSessionEnd(MavenSession session) {
        if (TurboBuilder.isTurboBuilder(session)) {
            checkBuilderAndPhase(session);
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
}
