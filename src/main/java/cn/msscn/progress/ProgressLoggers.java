package cn.msscn.progress;

import java.util.function.Consumer;

/** Adapters that turn any logging call into a {@link ProgressLogger}. */
public final class ProgressLoggers {

    private ProgressLoggers() {
    }

    /** Bridge to SLF4J / Log4j / Logback / JUL without a compile dependency. */
    public static ProgressLogger of(Consumer<String> consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("consumer must not be null");
        }
        return new ProgressLogger() {
            @Override
            public void log(String message) {
                consumer.accept(message);
            }
        };
    }
}
