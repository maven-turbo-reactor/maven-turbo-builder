package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.PhaseOrderPatcher.isPackage;

import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.Maven;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;

/**
 * @author Sergey Chernov
 */
@Named
@Singleton
public class TurboProjectExecutionListener implements ProjectExecutionListener {

    @Override
    public void beforeProjectExecution(ProjectExecutionEvent event) {
    }

    @Override
    public void beforeProjectLifecycleExecution(ProjectExecutionEvent event) {
        CurrentProjectExecution.ifPresent(execution -> {
            execution.packageMojos = event.getExecutionPlan().stream()
                .filter(mojo -> {
                    String lifecyclePhase = mojo.getLifecyclePhase();
                    return lifecyclePhase != null && isPackage(lifecyclePhase);
                })
                .collect(Collectors.toList());

            if (isReorderPhases()) {
                TurboBuilderConfig config = TurboBuilderConfig.fromSession(event.getSession());
                PhaseOrderPatcher.reorderPhases(config, event.getExecutionPlan(), mojoExecution ->
                    Objects.toString(mojoExecution.getLifecyclePhase(), ""));
            }
        });
    }

    boolean isReorderPhases() {
        String mavenVersion = Maven.class.getPackage().getImplementationVersion();
        // since Maven 4 instead of patching DefaultLifecycles we patch the execution plan
        return mavenVersion != null && mavenVersion.startsWith("4.");
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent event) {
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent event) {
    }
}
