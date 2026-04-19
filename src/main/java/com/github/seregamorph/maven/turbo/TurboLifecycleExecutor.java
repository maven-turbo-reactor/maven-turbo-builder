package com.github.seregamorph.maven.turbo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycleExecutor;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.LifecycleExecutionException;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.lifecycle.LifecycleNotFoundException;
import org.apache.maven.lifecycle.LifecyclePhaseNotFoundException;
import org.apache.maven.lifecycle.MavenExecutionPlan;
import org.apache.maven.lifecycle.internal.ExecutionPlanItem;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.InvalidPluginDescriptorException;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoNotFoundException;
import org.apache.maven.plugin.PluginDescriptorParsingException;
import org.apache.maven.plugin.PluginManagerException;
import org.apache.maven.plugin.PluginNotFoundException;
import org.apache.maven.plugin.PluginResolutionException;
import org.apache.maven.plugin.prefix.NoPluginFoundForPrefixException;
import org.apache.maven.plugin.version.PluginVersionResolutionException;
import org.apache.maven.project.MavenProject;

/**
 * Custom {@link LifecycleExecutor} that reorders actual lifecycle phases to properly report build plan in
 * <pre>
 * mvn org.codehaus.mojo:buildplan-maven-plugin:list -b turbo
 * </pre>
 *
 * @author Sergey Chernov
 */
@Named
@Singleton
@org.eclipse.sisu.Priority(10) // maven 3
@org.apache.maven.api.di.Priority(10) // maven 4
public class TurboLifecycleExecutor implements LifecycleExecutor {

    private final Object delegateSync = new Object();

    private final DefaultLifecycles defaultLifeCycles;
    private final Provider<DefaultLifecycleExecutor> defaultLifecycleExecutorProvider;

    private DefaultLifecycleExecutor delegate;

    @Inject
    public TurboLifecycleExecutor(
        DefaultLifecycles defaultLifeCycles,
        Provider<DefaultLifecycleExecutor> defaultLifecycleExecutorProvider
    ) {
        this.defaultLifeCycles = defaultLifeCycles;
        this.defaultLifecycleExecutorProvider = defaultLifecycleExecutorProvider;
    }

    @Override
    public Set<Plugin> getPluginsBoundByDefaultToAllLifecycles(String packaging) {
        return delegate().getPluginsBoundByDefaultToAllLifecycles(packaging);
    }

    @Override
    public MavenExecutionPlan calculateExecutionPlan(MavenSession session, String... tasks) throws PluginNotFoundException,
        PluginResolutionException, PluginDescriptorParsingException, MojoNotFoundException, NoPluginFoundForPrefixException,
        InvalidPluginDescriptorException, PluginManagerException, LifecyclePhaseNotFoundException, LifecycleNotFoundException,
        PluginVersionResolutionException {
        MavenExecutionPlan executionPlan = delegate().calculateExecutionPlan(session, tasks);
        return resolveExecutionPlan(session, executionPlan);
    }

    @Override
    public MavenExecutionPlan calculateExecutionPlan(MavenSession session, boolean setup, String... tasks) throws PluginNotFoundException,
        PluginResolutionException, PluginDescriptorParsingException, MojoNotFoundException, NoPluginFoundForPrefixException,
        InvalidPluginDescriptorException, PluginManagerException, LifecyclePhaseNotFoundException, LifecycleNotFoundException,
        PluginVersionResolutionException {
        MavenExecutionPlan executionPlan = delegate().calculateExecutionPlan(session, setup, tasks);
        return resolveExecutionPlan(session, executionPlan);
    }

    @Override
    public void execute(MavenSession session) {
        delegate().execute(session);
    }

    @Override
    public void calculateForkedExecutions(MojoExecution mojoExecution, MavenSession session) throws MojoNotFoundException,
        PluginNotFoundException, PluginResolutionException, PluginDescriptorParsingException, NoPluginFoundForPrefixException,
        InvalidPluginDescriptorException, LifecyclePhaseNotFoundException, LifecycleNotFoundException, PluginVersionResolutionException {
        delegate().calculateForkedExecutions(mojoExecution, session);
    }

    @Override
    public List<MavenProject> executeForkedExecutions(MojoExecution mojoExecution, MavenSession session) throws LifecycleExecutionException {
        return delegate().executeForkedExecutions(mojoExecution, session);
    }

    private MavenExecutionPlan resolveExecutionPlan(MavenSession session, MavenExecutionPlan defaultExecutionPlan) {
        if (TurboBuilder.isTurboBuilder(session)) {
            TurboBuilderConfig config = TurboBuilderConfig.fromSession(session);
            List<MojoExecution> mojoExecutions = defaultExecutionPlan.getMojoExecutions();
            PhaseOrderPatcher.reorderPhases(config.isTurboTestCompile(), mojoExecutions, MojoUtils::getMojoPhase);
            List<ExecutionPlanItem> executionPlanItems = new ArrayList<>();
            for (MojoExecution mojoExecution : mojoExecutions) {
                executionPlanItems.add(new ExecutionPlanItem(mojoExecution));
            }
            return new MavenExecutionPlan(executionPlanItems, defaultLifeCycles);
        } else {
            return defaultExecutionPlan;
        }
    }

    private DefaultLifecycleExecutor delegate() {
        synchronized (delegateSync) {
            if (delegate == null) {
                delegate = defaultLifecycleExecutorProvider.get();
            }
            return delegate;
        }
    }
}
