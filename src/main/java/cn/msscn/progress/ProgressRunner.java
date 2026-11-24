package cn.msscn.progress;

import java.util.Collection;
import java.util.function.Consumer;

/** Runs a fixed number of execution steps while keeping a reporter in sync. */
public final class ProgressRunner {

    /** One unit of work; {@code index} is 1-based. */
    public interface Step {
        /**
         * Performs one unit of work.
         *
         * @param index the 1-based step number
         * @throws Exception if the step fails
         */
        void execute(int index) throws Exception;
    }

    private ProgressRunner() {
    }

    /**
     * Executes {@code steps} work items, advancing the reporter after each one.
     * On success the reporter is completed; on failure it is cancelled and the
     * original exception is rethrown.
     */
    public static void run(ProgressReporter reporter, int steps, Step step) throws Exception {
        if (reporter == null || step == null) {
            throw new IllegalArgumentException("reporter and step must not be null");
        }
        if (steps <= 0) {
            throw new IllegalArgumentException("steps must be >= 1");
        }
        reporter.start();
        try {
            for (int i = 1; i <= steps; i++) {
                step.execute(i);
                reporter.update(i);
            }
            reporter.complete();
        } catch (Exception e) {
            reporter.cancel();
            throw e;
        }
    }

    /** Convenience for tasks that do not throw checked exceptions (distinct name to avoid lambda ambiguity). */
    public static void runUnchecked(ProgressReporter reporter, int steps, final UncheckedStep step) {
        try {
            run(reporter, steps, new Step() {
                @Override
                public void execute(int index) {
                    step.execute(index);
                }
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Iterates over arbitrary items; a {@link Collection} also sets the total.
     * The consumer receives the item and its 1-based index.
     */
    public static <T> void forEach(ProgressReporter reporter, Iterable<T> items,
                                   IndexedConsumer<T> consumer) {
        if (reporter == null || items == null || consumer == null) {
            throw new IllegalArgumentException("reporter, items and consumer must not be null");
        }
        if (items instanceof Collection) {
            reporter.setTotal(((Collection<?>) items).size());
        }
        reporter.start();
        int index = 0;
        try {
            for (T item : items) {
                consumer.accept(item, ++index);
                reporter.update(index);
            }
            reporter.complete();
        } catch (RuntimeException e) {
            reporter.cancel();
            throw e;
        }
    }

    /** Same as above, ignoring the index. */
    public static <T> void forEach(ProgressReporter reporter, Iterable<T> items,
                                   final Consumer<T> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        forEach(reporter, items, new IndexedConsumer<T>() {
            @Override
            public void accept(T item, int index) {
                consumer.accept(item);
            }
        });
    }

    /** Runs a list of tasks (e.g. Runnables) as execution steps. */
    public static void runTasks(ProgressReporter reporter, Iterable<Runnable> tasks) {
        forEach(reporter, tasks, new Consumer<Runnable>() {
            @Override
            public void accept(Runnable task) {
                task.run();
            }
        });
    }

    /** Consumer that also receives the 1-based step index. */
    public interface IndexedConsumer<T> {
        /**
         * Consumes one item together with its 1-based position.
         *
         * @param item the current item
         * @param index the 1-based position
         */
        void accept(T item, int index);
    }

    /** Step variant without checked exceptions. */
    public interface UncheckedStep {
        /**
         * Performs one unit of work without checked exceptions.
         *
         * @param index the 1-based step number
         */
        void execute(int index);
    }
}
