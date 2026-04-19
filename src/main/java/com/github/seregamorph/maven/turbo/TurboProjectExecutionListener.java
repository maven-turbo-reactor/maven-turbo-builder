package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.PhaseOrderPatcher.isPackage;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.execution.ProjectExecutionEvent;
import org.apache.maven.execution.ProjectExecutionListener;
import org.apache.maven.plugin.MojoExecution;

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
            List<MojoExecution> mojoExecutions = event.getExecutionPlan();
            execution.packageMojos = mojoExecutions.stream()
                .filter(mojo -> {
                    String lifecyclePhase = mojo.getLifecyclePhase();
                    return lifecyclePhase != null && isPackage(lifecyclePhase);
                })
                .collect(Collectors.toList());

            TurboBuilderConfig config = TurboBuilderConfig.fromSession(event.getSession());
            boolean compileTestsBeforePackage = config.isTurboTestCompile()
                || TestJarSupport.hasTestJar(mojoExecutions);
            PhaseOrderPatcher.reorderPhases(compileTestsBeforePackage, mojoExecutions, MojoUtils::getMojoPhase);
        });
    }

    @Override
    public void afterProjectExecutionSuccess(ProjectExecutionEvent event) {
    }

    @Override
    public void afterProjectExecutionFailure(ProjectExecutionEvent event) {
    }
}
