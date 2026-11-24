package cn.msscn.progress;

/** Lifecycle state of a progress bar. */
public enum ProgressStatus {
    /** Created but not started yet. */
    PENDING,
    /** Currently advancing. */
    RUNNING,
    /** Finished successfully at the target value. */
    COMPLETED,
    /** Stopped early at the current value. */
    CANCELLED
}
