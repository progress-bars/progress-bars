package cn.msscn.progress;

/** Writes one log record per frame line. Works with JUL, SLF4J, Log4j via {@link ProgressLogger}. */
public final class LoggingSink implements ProgressSink {

    private final ProgressLogger logger;
    private final String marker;

    /** Creates a sink using JUL with the {@code [PROGRESS] } prefix. */
    public LoggingSink() {
        this(new JdkProgressLogger(), "[PROGRESS] ");
    }

    /**
     * Creates a sink with the default {@code [PROGRESS] } prefix.
     *
     * @param logger the logger bridge to write through
     */
    public LoggingSink(ProgressLogger logger) {
        this(logger, "[PROGRESS] ");
    }

    /** Log through a lambda, e.g. {@code LoggingSink.of(msg -> log.info("{}", msg))}. */
    public static LoggingSink of(java.util.function.Consumer<String> consumer) {
        return new LoggingSink(ProgressLoggers.of(consumer));
    }

    /** Lambda logger with a custom per-line prefix (empty string disables it). */
    public static LoggingSink of(java.util.function.Consumer<String> consumer, String marker) {
        return new LoggingSink(ProgressLoggers.of(consumer), marker);
    }

    /**
     * Creates a sink with an explicit logger and per-line prefix.
     *
     * @param logger the logger bridge to write through
     * @param marker prefix for each line, or null/empty for none
     * @throws IllegalArgumentException if {@code logger} is null
     */
    public LoggingSink(ProgressLogger logger, String marker) {
        if (logger == null) {
            throw new IllegalArgumentException("logger must not be null");
        }
        this.logger = logger;
        this.marker = marker == null ? "" : marker;
    }

    @Override
    public void write(String frame) {
        if (frame == null) {
            return;
        }
        String[] lines = frame.split("\n", -1);
        for (String line : lines) {
            if (line.length() > 0) {
                logger.log(marker + line);
            }
        }
    }

    @Override
    public void close() {
        // no resource to release
    }
}
