package com.github.seregamorph.maven.turbo;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Sergey Chernov
 */
class SignalingExecutorCompletionService {

    private static final Logger logger = LoggerFactory.getLogger(SignalingExecutorCompletionService.class);

    static final ThreadLocal<Consumer<MavenProject>> currentSignaler = new ThreadLocal<>();

    private final ExecutorService executor;
    private final BlockingQueue<Try<ProjectEvent>> eventQueue;

    SignalingExecutorCompletionService(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
        this.eventQueue = new LinkedBlockingQueue<>();
    }

    /**
     * Notify scheduler that the current project is now available for downstream dependencies, so
     * they can be scheduled.
     *
     * @param project built project
     */
    static void signal(MavenProject project) {
        Consumer<MavenProject> signaler = currentSignaler.get();
        if (signaler == null) {
            throw new IllegalStateException("Current thread does not have a signaler");
        }
        signaler.accept(project);
    }

    Future<MavenProject> submit(int order, Callable<MavenProject> buildCallable) {
        Objects.requireNonNull(buildCallable);
        return executor.submit(new OrderedCallable<>(order, () -> {
            AtomicBoolean signaled = new AtomicBoolean(false);
            currentSignaler.set(mavenProject -> {
                // No race condition here with "if (!signaled.get())" block, because it's the same thread.
                // This callback is eventually called from buildCallable.call() few lines below.
                if (!signaled.compareAndSet(false, true)) {
                    // this should never happen
                    throw new IllegalStateException("Current thread has already been signaled");
                }
                logger.debug("Project {} signaled to be ready for building its downstream dependencies", mavenProject);
                eventQueue.add(Try.success(ProjectEvent.signaled(mavenProject)));
            });
            try {
                MavenProject result = buildCallable.call();
                if (!signaled.get()) {
                    eventQueue.add(Try.success(ProjectEvent.signaled(result)));
                }
                eventQueue.add(Try.success(ProjectEvent.completed(result)));
                return result;
            } catch (Throwable e) {
                eventQueue.add(Try.failure(e));
                if (e instanceof Exception) {
                    throw e;
                } else {
                    throw new RuntimeException(e);
                }
            } finally {
                currentSignaler.remove();
            }
        }));
    }

    /**
     * Take the next project event. Each submitted build produces exactly one signaled event (sent either via
     * {@link #signal(MavenProject)} during the build or after the build) followed by exactly one completed event. If
     * the build throws, this method throws {@link ExecutionException} in place of the remaining events.
     *
     * @return the next project event
     */
    ProjectEvent takeEvent() throws InterruptedException, ExecutionException {
        Try<ProjectEvent> t = eventQueue.take();
        return t.get();
    }

    static final class ProjectEvent {

        private final MavenProject project;
        private final boolean completed;

        private ProjectEvent(MavenProject project, boolean completed) {
            this.project = project;
            this.completed = completed;
        }

        static ProjectEvent signaled(MavenProject project) {
            return new ProjectEvent(project, false);
        }

        static ProjectEvent completed(MavenProject project) {
            return new ProjectEvent(project, true);
        }

        MavenProject getProject() {
            return project;
        }

        /**
         * @return true if all phases of the project build are completed, false if the project is only ready for
         * building its downstream dependencies
         */
        boolean isCompleted() {
            return completed;
        }
    }

    private abstract static class Try<T> {
        abstract T get() throws ExecutionException;

        static <T> Try<T> success(T value) {
            return new Try<T>() {
                @Override
                T get() {
                    return value;
                }
            };
        }

        static <T> Try<T> failure(Throwable e) {
            return new Try<T>() {
                @Override
                T get() throws ExecutionException {
                    throw new ExecutionException(e);
                }
            };
        }
    }
}
