package cn.msscn.progress;

/**
 * Async callbacks. Default (no-op) methods mean you only implement what you need.
 *
 * <p>Callbacks are dispatched on the executor configured in
 * {@link ProgressConfig.Builder#listenerExecutor(java.util.concurrent.Executor)},
 * by default a shared daemon thread. Always treat snapshots as immutable.</p>
 */
public interface ProgressListener {

    /**
     * Called when progress advances.
     *
     * @param snapshot the current immutable state
     */
    default void onProgress(ProgressSnapshot snapshot) {
    }

    /**
     * Called once when the task completes.
     *
     * @param snapshot the final state
     */
    default void onComplete(ProgressSnapshot snapshot) {
    }

    /**
     * Called once when the task is cancelled.
     *
     * @param snapshot the final state
     */
    default void onCancel(ProgressSnapshot snapshot) {
    }
}
