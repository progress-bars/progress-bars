package cn.msscn.progress;

/**
 * Generic progress sink for callers: uploaders, downloaders, batch jobs, etc.
 * depend on this interface instead of a concrete bar, so the same code can
 * drive console output, logging, or a silent API-only bar.
 */
public interface ProgressReporter {

    /** A reporter that accepts updates but keeps no state. */
    static ProgressReporter noop() {
        return new ProgressReporter() {
            @Override public ProgressReporter start() { return this; }
            @Override public ProgressReporter update(double value) { return this; }
            @Override public ProgressReporter increment(double delta) { return this; }
            @Override public ProgressReporter setTotal(double total) { return this; }
            @Override public ProgressReporter complete() { return this; }
            @Override public ProgressReporter cancel() { return this; }
            @Override public double getPercent() { return 0.0; }
            @Override public boolean isDone() { return false; }
            @Override public ProgressSnapshot snapshot() {
                return new ProgressSnapshot(1, 1, "noop", 0, 0, 0, 0,
                        ProgressStatus.PENDING, 0, 0, false);
            }
        };
    }

    /**
     * Moves the reporter into the running state.
     *
     * @return this reporter
     */
    ProgressReporter start();

    /** Absolute progress value (bytes, items, steps - whatever the caller uses). */
    ProgressReporter update(double value);

    /** Relative progress value. */
    ProgressReporter increment(double delta);

    /** Change the target value; the current value is clamped to it. */
    ProgressReporter setTotal(double total);

    /**
     * Marks the task fully done and fires completion callbacks.
     *
     * @return this reporter
     */
    ProgressReporter complete();

    /**
     * Stops at the current value and fires cancellation callbacks.
     *
     * @return this reporter
     */
    ProgressReporter cancel();

    /** @return the percentage in [0, 100] */
    double getPercent();

    /** @return true when the task is completed or cancelled */
    boolean isDone();

    default boolean isIndeterminate() {
        return false;
    }

    /**
     * Returns an immutable view of the current state.
     *
     * @return the current snapshot
     */
    ProgressSnapshot snapshot();
}
