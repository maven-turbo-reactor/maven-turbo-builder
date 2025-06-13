package com.github.seregamorph.maven.turbo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.DefaultLifecycles;
import org.apache.maven.lifecycle.internal.BuildThreadFactory;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ProjectBuildList;
import org.apache.maven.lifecycle.internal.ProjectSegment;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.lifecycle.internal.builder.Builder;
import org.apache.maven.lifecycle.internal.builder.multithreaded.ConcurrencyDependencyGraph;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

/**
 * Custom maven project builder. It's rewritten from original
 * {@link org.apache.maven.lifecycle.internal.builder.multithreaded.MultiThreadedBuilder}
 *
 * Use "-b turbo" maven parameters like "mvn clean verify -b turbo" to activate.
 * Or specify "-bturbo" in the .mvn/maven.config file to use by default.
 * Schedules downstream dependencies right after the package phase, also it's coupled with
 * {@link TurboMojosExecutionStrategy} reordering package and test phases.
 *
 * @author Sergey Chernov
 */
@Singleton
@Named(TurboBuilder.BUILDER_TURBO)
public class TurboBuilder implements Builder {

    public static final String BUILDER_TURBO = "turbo";

    private final LifecycleModuleBuilder lifecycleModuleBuilder;
    private final Logger logger;

    @Inject
    public TurboBuilder(
            DefaultLifecycles defaultLifeCycles,
            LifecycleModuleBuilder lifecycleModuleBuilder,
            TurboBuilderConfig config,
            Logger logger
    ) {
        this.lifecycleModuleBuilder = lifecycleModuleBuilder;
        this.logger = logger;

        // we patch the default lifecycle in-place only when "-b turbo" parameter is specified
        defaultLifeCycles.getLifeCycles().forEach(lifecycle -> {
            if ("default".equals(lifecycle.getId())) {
                logger.warn("Turbo builder: patching default lifecycle 🏎️ (reorder package and test phases)");
                DefaultLifecyclePatcher.patchDefaultLifecycle(config, lifecycle.getPhases());
            }
        });
    }

    @Override
    public void build(
        MavenSession session,
        ReactorContext reactorContext,
        ProjectBuildList projectBuilds,
        List<TaskSegment> taskSegments,
        ReactorBuildStatus reactorBuildStatus
    ) throws InterruptedException {
        int nThreads = Math.min(
            session.getRequest().getDegreeOfConcurrency(),
            session.getProjects().size());
        logger.info("TurboBuilder will use " + nThreads + " threads to build "
            + session.getProjects().size() + " modules");
        boolean parallel = nThreads > 1;
        // Propagate the parallel flag to the root session and all of the cloned sessions in each project segment
        session.setParallel(parallel);
        for (ProjectSegment segment : projectBuilds) {
            segment.getSession().setParallel(parallel);
        }
        // executor supporting task ordering, prioritize building modules that have more downstream dependencies
        ExecutorService executor = new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(), new BuildThreadFactory()) {
            @Override
            protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
                return new OrderedFutureTask<>((OrderedCallable<T>) callable);
            }
        };
        SignalingExecutorCompletionService service = new SignalingExecutorCompletionService(executor);

        for (TaskSegment taskSegment : taskSegments) {
            ProjectBuildList segmentProjectBuilds = projectBuilds.getByTaskSegment(taskSegment);
            Map<MavenProject, ProjectSegment> projectBuildMap = projectBuilds.selectSegment(taskSegment);
            try {
                ConcurrencyDependencyGraph analyzer =
                    new ConcurrencyDependencyGraph(segmentProjectBuilds, session.getProjectDependencyGraph());
                multiThreadedProjectTaskSegmentBuild(
                    analyzer, reactorContext, session, service, taskSegment, projectBuildMap);
                if (reactorContext.getReactorBuildStatus().isHalted()) {
                    break;
                }
            } catch (Exception e) {
                session.getResult().addException(e);
                break;
            }
        }

        executor.shutdown();
        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private void multiThreadedProjectTaskSegmentBuild(
        ConcurrencyDependencyGraph analyzer,
        ReactorContext reactorContext,
        MavenSession rootSession,
        SignalingExecutorCompletionService service,
        TaskSegment taskSegment,
        Map<MavenProject, ProjectSegment> projectBuildList
    ) {
        // gather artifactIds which are not unique so that the respective thread names can be extended with the groupId
        Set<String> duplicateArtifactIds = gatherDuplicateArtifactIds(projectBuildList.keySet());

        // collect all submitted tasks to join them at the end
        List<Future<MavenProject>> tasks = new ArrayList<>();
        // schedule independent projects
        for (MavenProject mavenProject : analyzer.getRootSchedulableBuilds()) {
            ProjectSegment projectSegment = projectBuildList.get(mavenProject);
            logger.debug("Scheduling: " + projectSegment.getProject());
            Callable<MavenProject> cb = createBuildCallable(
                rootSession, projectSegment, reactorContext, taskSegment, duplicateArtifactIds);
            List<MavenProject> downstreamDependencies = rootSession.getProjectDependencyGraph()
                .getDownstreamProjects(mavenProject, false);
            // negate size for descending order
            tasks.add(service.submit(-downstreamDependencies.size(), cb));
        }

        // for each finished project
        for (int i = 0; i < analyzer.getNumberOfBuilds(); i++) {
            try {
                MavenProject projectBuild = service.takeSignaled();
                if (reactorContext.getReactorBuildStatus().isHalted()) {
                    return;
                }

                // MNG-6170: Only schedule other modules from reactor if we have more modules to build than one.
                if (analyzer.getNumberOfBuilds() > 1) {
                    List<MavenProject> newItemsThatCanBeBuilt = analyzer.markAsFinished(projectBuild);
                    for (MavenProject mavenProject : newItemsThatCanBeBuilt) {
                        ProjectSegment scheduledDependent = projectBuildList.get(mavenProject);
                        logger.debug("Scheduling: " + scheduledDependent);
                        Callable<MavenProject> cb = createBuildCallable(
                            rootSession,
                            scheduledDependent,
                            reactorContext,
                            taskSegment,
                            duplicateArtifactIds);
                        List<MavenProject> downstreamDependencies = rootSession.getProjectDependencyGraph()
                            .getDownstreamProjects(mavenProject, false);
                        tasks.add(service.submit(-downstreamDependencies.size(), cb));
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                rootSession.getResult().addException(e);
                return;
            }
        }

        for (Future<MavenProject> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException | ExecutionException e) {
                rootSession.getResult().addException(e);
                return;
            }
        }
    }

    private Callable<MavenProject> createBuildCallable(
        MavenSession rootSession,
        ProjectSegment projectBuild,
        ReactorContext reactorContext,
        TaskSegment taskSegment,
        Set<String> duplicateArtifactIds
    ) {
        return () -> {
            final Thread currentThread = Thread.currentThread();
            final String originalThreadName = currentThread.getName();
            final MavenProject project = projectBuild.getProject();

            final String threadNameSuffix = duplicateArtifactIds.contains(project.getArtifactId())
                ? project.getGroupId() + ":" + project.getArtifactId()
                : project.getArtifactId();
            currentThread.setName("mvn-turbo-builder-" + threadNameSuffix);

            try {
                lifecycleModuleBuilder.buildProject(
                    projectBuild.getSession(), rootSession, reactorContext, project, taskSegment);

                return projectBuild.getProject();
            } finally {
                currentThread.setName(originalThreadName);
            }
        };
    }

    private static Set<String> gatherDuplicateArtifactIds(Set<MavenProject> projects) {
        Set<String> artifactIds = new HashSet<>(projects.size());
        Set<String> duplicateArtifactIds = new HashSet<>();
        for (MavenProject project : projects) {
            if (!artifactIds.add(project.getArtifactId())) {
                duplicateArtifactIds.add(project.getArtifactId());
            }
        }
        return duplicateArtifactIds;
    }
}
