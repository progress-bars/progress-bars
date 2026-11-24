package cn.msscn.progress;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Default {@link ProgressLogger} backed by {@code java.util.logging}. */
public final class JdkProgressLogger implements ProgressLogger {

    private final Logger delegate;
    private final Level level;

    /**
     * Creates a logger named {@code progress} at {@code INFO} level.
     */
    public JdkProgressLogger() {
        this("progress", Level.INFO);
    }

    /**
     * Creates a logger with an explicit name and level.
     *
     * @param loggerName the JUL logger name, or null for {@code progress}
     * @param level the JUL level, or null for {@code INFO}
     */
    public JdkProgressLogger(String loggerName, Level level) {
        this.delegate = Logger.getLogger(loggerName == null ? "progress" : loggerName);
        this.level = level == null ? Level.INFO : level;
    }

    @Override
    public void log(String message) {
        delegate.log(level, message);
    }
}
