package cn.msscn.progress;

/** Minimal bridge so any logging framework can be plugged in without a compile dependency. */
@FunctionalInterface
public interface ProgressLogger {

    /**
     * Emits one log record.
     *
     * @param message the already-rendered line to log
     */
    void log(String message);
}
