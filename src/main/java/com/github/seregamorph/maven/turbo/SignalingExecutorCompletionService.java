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

/**
 * @author Sergey Chernov
 */
class SignalingExecutorCompletionService {

    static final ThreadLocal<Consumer<MavenProject>> currentSignaler = new ThreadLocal<>();

    private final ExecutorService executor;
    private final BlockingQueue<Try<MavenProject>> signaledQueue;

    SignalingExecutorCompletionService(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor);
        this.signaledQueue = new LinkedBlockingQueue<>();
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
                signaled.set(true);
                signaledQueue.add(Try.success(mavenProject));
            });
            try {
                MavenProject result = buildCallable.call();
                if (!signaled.get()) {
                    signaledQueue.add(Try.success(result));
                }
                return result;
            } catch (Throwable e) {
                signaledQueue.add(Try.failure(e));
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

    MavenProject takeSignaled() throws InterruptedException, ExecutionException {
        Try<MavenProject> t = signaledQueue.take();
        return t.get();
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
