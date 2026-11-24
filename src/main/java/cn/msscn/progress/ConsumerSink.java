package cn.msscn.progress;

import java.util.function.Consumer;

/**
 * Sink that hands rendered frames to your own code.
 *
 * <p>Use it to integrate with a host application's logging framework, an event
 * bus, a UI text area, or plain {@code System.out.print}:</p>
 *
 * <pre>
 * ProgressConfig.builder()
 *         .sink(ConsumerSink.lines(line -&gt; log.info("{}", line)))
 *         .build();
 * </pre>
 *
 * <p>Two modes are available: {@link #of(Consumer)} passes the whole frame
 * (multiple lines are '\\n' separated) to one consumer, {@link #lines(Consumer)}
 * passes each non-empty line separately.</p>
 */
public final class ConsumerSink implements ProgressSink {

    private final Consumer<String> frameConsumer;
    private final Consumer<String> lineConsumer;
    private final Runnable onClose;

    private ConsumerSink(Consumer<String> frameConsumer, Consumer<String> lineConsumer,
                         Runnable onClose) {
        this.frameConsumer = frameConsumer;
        this.lineConsumer = lineConsumer;
        this.onClose = onClose;
    }

    /** One callback per rendered frame. */
    public static ConsumerSink of(Consumer<String> onFrame) {
        return of(onFrame, null);
    }

    /** One callback per rendered frame, plus a hook invoked from {@link #close()}. */
    public static ConsumerSink of(Consumer<String> onFrame, Runnable onClose) {
        if (onFrame == null) {
            throw new IllegalArgumentException("frame consumer must not be null");
        }
        return new ConsumerSink(onFrame, null, onClose);
    }

    /** One callback per non-empty line; convenient for log frameworks. */
    public static ConsumerSink lines(Consumer<String> onLine) {
        return lines(onLine, null);
    }

    /** One callback per non-empty line, plus a hook invoked from {@link #close()}. */
    public static ConsumerSink lines(Consumer<String> onLine, Runnable onClose) {
        if (onLine == null) {
            throw new IllegalArgumentException("line consumer must not be null");
        }
        return new ConsumerSink(null, onLine, onClose);
    }

    @Override
    public void write(String frame) {
        if (frame == null) {
            return;
        }
        if (lineConsumer == null) {
            frameConsumer.accept(frame);
            return;
        }
        String[] lines = frame.split("\n", -1);
        for (String line : lines) {
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            if (line.length() > 0) {
                lineConsumer.accept(line);
            }
        }
    }

    @Override
    public void flush() {
        // Nothing buffered here.
    }

    @Override
    public void close() {
        if (onClose != null) {
            onClose.run();
        }
    }
}
