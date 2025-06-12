package com.github.seregamorph.maven.turbo;

import static com.github.seregamorph.maven.turbo.DefaultLifecyclePatcher.isAnyTest;
import static com.github.seregamorph.maven.turbo.DefaultLifecyclePatcher.isPackage;
import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.getProperty;
import static com.github.seregamorph.maven.turbo.MavenPropertyUtils.isEmptyOrTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionRunner;
import org.apache.maven.plugin.MojosExecutionStrategy;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.project.MavenProject;

/**
 * @author Sergey Chernov
 */
public class TurboMojosExecutionStrategy implements MojosExecutionStrategy {

    @Override
    public void execute(
        List<MojoExecution> mojos,
        MavenSession session,
        MojoExecutionRunner mojoRunner
    ) throws LifecycleExecutionException {
        List<MojoExecution> packageMojos = mojos.stream()
            .filter(mojo -> {
                String lifecyclePhase = mojo.getLifecyclePhase();
                return lifecyclePhase != null && isPackage(lifecyclePhase);
            })
            .collect(Collectors.toList());

        List<MojoExecution> executedPackageMojos = new ArrayList<>();
        MavenProject currentProject = session.getCurrentProject();
        // There can be scenarios when we use TurboBuilder as default, but disable per project, property or via profile,
        // when it's known that the downstream dependencies should be only scheduled when all phases are completed.
        boolean skipTurboSignal = isEmptyOrTrue(getProperty(session, currentProject, "skipTurboSignal"));
        boolean signaled = skipTurboSignal;
        for (MojoExecution mojoExecution : mojos) {
            if (!signaled && packageMojos.isEmpty()) {
                String phase = mojoExecution.getLifecyclePhase();
                if (phase == null) {
                    MojoDescriptor mojoDescriptor = mojoExecution.getMojoDescriptor();
                    if (mojoDescriptor != null) {
                        phase = mojoDescriptor.getPhase();
                    }
                }
                if (phase != null && isAnyTest(phase)) {
                    signaled = true;
                    // signal before tests
                    SignalingExecutorCompletionService.signal(currentProject);
                }
            }
            mojoRunner.run(mojoExecution);
            if (!signaled) {
                if (packageMojos.contains(mojoExecution)) {
                    executedPackageMojos.add(mojoExecution);
                    if (packageMojos.equals(executedPackageMojos)) {
                        signaled = true;
                        // signal after package
                        SignalingExecutorCompletionService.signal(currentProject);
                    }
                }
            }
        }
    }
}
