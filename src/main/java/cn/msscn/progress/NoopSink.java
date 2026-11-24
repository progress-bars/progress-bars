package cn.msscn.progress;

/** Sink for pure API usage: values are readable but nothing is printed. */
public final class NoopSink implements ProgressSink {

    /** Shared no-op sink for API-only usage. */
    public static final NoopSink INSTANCE = new NoopSink();

    private NoopSink() {
    }

    @Override
    public void write(String frame) {
    }

    @Override
    public void close() {
    }
}
