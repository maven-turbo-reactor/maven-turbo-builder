package com.github.seregamorph.maven.turbo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.graph.DefaultProjectDependencyGraph;
import org.apache.maven.lifecycle.internal.LifecycleModuleBuilder;
import org.apache.maven.lifecycle.internal.ProjectBuildList;
import org.apache.maven.lifecycle.internal.ProjectSegment;
import org.apache.maven.lifecycle.internal.ReactorBuildStatus;
import org.apache.maven.lifecycle.internal.ReactorContext;
import org.apache.maven.lifecycle.internal.TaskSegment;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class TurboBuilderTest {

    private static final long AWAIT_SECONDS = 10;
    // time given to a wrong scheduler to start a waiting project too early
    private static final long ABSENCE_MILLIS = 200;

    private final List<String> events = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private final Map<MavenProject, ProjectBuild> builds = new HashMap<>();
    private ReactorBuildStatus reactorBuildStatus;

    @Test
    public void shouldScheduleWaitingProjectAfterFullUpstreamCompletion() throws Exception {
        var core = project("core");
        var app = project("app", core);
        var report = waitingProject("report", core);
        builds.put(core, project -> {
            signal(project);
            // app is scheduled by the signal and fully built while core still runs its tests
            await("end:app");
            assertAbsent("start:report");
        });

        var result = build(List.of(core, app, report));

        assertEquals(List.of(), result.getExceptions());
        assertOrder("end:app", "end:core", "start:report", "end:report");
    }

    @Test
    public void shouldWaitForTransitiveUpstreamCompletion() throws Exception {
        var lib = project("lib");
        var core = project("core", lib);
        var report = waitingProject("report", core);
        builds.put(lib, project -> {
            signal(project);
            // core is fully built while lib still runs its tests
            await("end:core");
            assertAbsent("start:report");
        });

        var result = build(List.of(lib, core, report));

        assertEquals(List.of(), result.getExceptions());
        assertOrder("end:core", "end:lib", "start:report", "end:report");
    }

    @Test
    public void shouldWaitForUpstreamWithoutTurboSignal() throws Exception {
        var core = project("core");
        var report = waitingProject("report", core);
        // e.g. skipTurboSignal or no package phase
        builds.put(core, project -> {
        });

        var result = build(List.of(core, report));

        assertEquals(List.of(), result.getExceptions());
        assertOrder("start:core", "end:core", "start:report", "end:report");
    }

    @Test
    public void shouldSkipWaitingProjectOnUpstreamFailureAtEnd() throws Exception {
        var core = project("core");
        var app = project("app", core);
        var report = waitingProject("report", core);
        var other = project("other");
        builds.put(core, project -> {
            signal(project);
            await("end:app");
            // test failure with --fail-at-end, the way LifecycleModuleBuilder handles it
            reactorBuildStatus.blackList(project);
            record("fail:core");
        });

        var result = build(List.of(core, app, report, other));

        assertEquals(List.of(), result.getExceptions());
        assertOrder("end:app", "fail:core", "skip:report");
        assertTrue(events.contains("end:other"), events::toString);
        assertFalse(events.contains("start:report"), events::toString);
    }

    @Test
    public void shouldNotScheduleWaitingProjectOnUpstreamFailFast() throws Exception {
        var core = project("core");
        var app = project("app", core);
        var report = waitingProject("report", core);
        builds.put(core, project -> {
            signal(project);
            await("end:app");
            // test failure with --fail-fast, the way LifecycleModuleBuilder handles it
            reactorBuildStatus.halt();
            record("fail:core");
        });

        var result = build(List.of(core, app, report));

        assertEquals(List.of(), result.getExceptions());
        assertOrder("end:app", "fail:core");
        assertFalse(events.stream().anyMatch(event -> event.endsWith(":report")), events::toString);
    }

    @Test
    public void shouldNotScheduleWaitingProjectOnUpstreamException() throws Exception {
        var core = project("core");
        var report = waitingProject("report", core);
        builds.put(core, project -> {
            signal(project);
            throw new IllegalStateException("core failure");
        });

        var result = build(List.of(core, report));

        assertEquals(1, result.getExceptions().size());
        assertFalse(events.stream().anyMatch(event -> event.endsWith(":report")), events::toString);
    }

    private DefaultMavenExecutionResult build(List<MavenProject> projects) throws Exception {
        var request = new DefaultMavenExecutionRequest();
        request.setDegreeOfConcurrency(4);
        var result = new DefaultMavenExecutionResult();
        var session = new MavenSession(null, null, request, result);
        session.setProjects(projects);
        var graph = new DefaultProjectDependencyGraph(projects);
        session.setProjectDependencyGraph(graph);
        reactorBuildStatus = new ReactorBuildStatus(graph);
        var reactorContext = new ReactorContext(result, getClass().getClassLoader(), reactorBuildStatus);

        var taskSegment = new TaskSegment(false);
        var projectSegments = new ArrayList<ProjectSegment>();
        for (MavenProject project : projects) {
            projectSegments.add(new ProjectSegment(project, taskSegment, session.clone()));
        }

        var lifecycleModuleBuilder = new LifecycleModuleBuilder(null, null, null, List.of(), null) {
            @Override
            public void buildProject(
                MavenSession session,
                MavenSession rootSession,
                ReactorContext reactorContext,
                MavenProject currentProject,
                TaskSegment taskSegment
            ) {
                String artifactId = currentProject.getArtifactId();
                if (reactorContext.getReactorBuildStatus().isHaltedOrBlacklisted(currentProject)) {
                    record("skip:" + artifactId);
                    return;
                }
                record("start:" + artifactId);
                try {
                    builds.getOrDefault(currentProject, TurboBuilderTest::signal).build(currentProject);
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                record("end:" + artifactId);
            }
        };

        new TurboBuilder(lifecycleModuleBuilder).build(session, reactorContext,
            new ProjectBuildList(projectSegments), List.of(taskSegment), reactorBuildStatus);
        return result;
    }

    private static MavenProject project(String artifactId, MavenProject... dependencies) {
        var project = new MavenProject();
        project.setGroupId("com.example");
        project.setArtifactId(artifactId);
        project.setVersion("1.0");
        for (MavenProject dependency : dependencies) {
            var modelDependency = new Dependency();
            modelDependency.setGroupId(dependency.getGroupId());
            modelDependency.setArtifactId(dependency.getArtifactId());
            modelDependency.setVersion(dependency.getVersion());
            project.getModel().addDependency(modelDependency);
        }
        return project;
    }

    private static MavenProject waitingProject(String artifactId, MavenProject... dependencies) {
        var project = project(artifactId, dependencies);
        project.getProperties().setProperty("turboWaitFullUpstream", "true");
        return project;
    }

    private static void signal(MavenProject project) {
        SignalingExecutorCompletionService.signal(project);
    }

    private void record(String event) {
        events.add(event);
        latch(event).countDown();
    }

    private CountDownLatch latch(String event) {
        return latches.computeIfAbsent(event, e -> new CountDownLatch(1));
    }

    private void await(String event) throws InterruptedException {
        assertTrue(latch(event).await(AWAIT_SECONDS, TimeUnit.SECONDS), () -> "Timeout waiting for " + event
            + ", events: " + events);
    }

    private void assertAbsent(String event) throws InterruptedException {
        assertFalse(latch(event).await(ABSENCE_MILLIS, TimeUnit.MILLISECONDS), () -> "Unexpected " + event
            + ", events: " + events);
    }

    private void assertOrder(String... expectedEvents) {
        var actualEvents = new ArrayList<>(events);
        int previousIndex = -1;
        for (String expectedEvent : expectedEvents) {
            int index = actualEvents.indexOf(expectedEvent);
            assertTrue(index > previousIndex, () -> "Expected order " + List.of(expectedEvents)
                + ", events: " + actualEvents);
            previousIndex = index;
        }
    }

    @FunctionalInterface
    private interface ProjectBuild {
        void build(MavenProject project) throws InterruptedException;
    }
}
