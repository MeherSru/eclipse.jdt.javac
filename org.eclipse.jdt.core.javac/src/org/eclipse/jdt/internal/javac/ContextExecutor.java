package org.eclipse.jdt.internal.javac;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.sun.tools.javac.util.Context;

/**
 * Executes tasks associated with a javac Context on a fixed set of
 * single-threaded executors ("stripes"), ensuring:
 *
 * - Thread confinement per Context (best-effort via hashing)
 * - Bounded thread count (no thread explosion)
 * - Synchronous API for callers
 * - Re-entrancy safety (no deadlock if already on the correct worker thread)
 *
 * This avoids the complexity of per-context executors while still respecting
 * javac's thread-affinity quirks.
 */
public final class ContextExecutor implements AutoCloseable {

	public static final ContextExecutor instance = new ContextExecutor(3);
    public static <T> T runContextTask(Supplier<T> task, Context context) {
    	return instance.run(task, context);
    }



    private final ExecutorService[] executors;
    private final int numExecutors;

    /**
     * ThreadLocal used to detect if we're already running on a worker thread.
     * Stores the stripe index for re-entrancy checks.
     */
    private final ThreadLocal<Integer> currentStripe = new ThreadLocal<>();

    public ContextExecutor(int numExecutors) {
        if (numExecutors <= 0) {
            throw new IllegalArgumentException("numExecutors must be > 0");
        }

        this.numExecutors = numExecutors;
        this.executors = new ExecutorService[numExecutors];

        for (int i = 0; i < numExecutors; i++) {
            final int stripeId = i;
            executors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ContextExecutor-" + stripeId);
                t.setDaemon(true);
                return t;
            });
        }
    }

    public ContextExecutor() {
        this(3); // default
    }

    /**
     * Run a task synchronously on the appropriate stripe for this Context.
     */
    public <T> T run(Supplier<T> task, Context context) {
        int stripe = stripe(context);

        // Re-entrancy: if we're already on the correct stripe thread, run directly
        Integer current = currentStripe.get();
        if (current != null && current == stripe) {
            return task.get();
        }

        Future<T> future = executors[stripe].submit(() -> {
            currentStripe.set(stripe);
            try {
                return task.get();
            } finally {
                currentStripe.remove();
            }
        });

        try {
            return future.get(); // synchronous
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while executing context task", e);
        } catch (ExecutionException e) {
            throw unwrap(e.getCause());
        }
    }

    /**
     * Runnable variant.
     */
    public void run(Runnable task, Context context) {
        run(() -> {
            task.run();
            return null;
        }, context);
    }

    /**
     * Determine which stripe this Context belongs to.
     */
    private int stripe(Context context) {
        return Math.abs(System.identityHashCode(context)) % numExecutors;
    }

    /**
     * Shutdown all executors.
     */
    @Override
    public void close() {
        for (ExecutorService executor : executors) {
            executor.shutdown();
        }
    }

    /**
     * Await termination of all executors.
     */
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);

        for (ExecutorService executor : executors) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 ||
                !executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Unwraps and rethrows the original cause as a RuntimeException.
     */
    private static RuntimeException unwrap(Throwable t) {
        if (t instanceof RuntimeException) {
            return (RuntimeException) t;
        }
        return new RuntimeException(t);
    }
}