package cn.msscn.progress;

import java.io.PrintStream;

/**
 * Writes each rendered frame to a {@link PrintStream} using {@code print} or
 * {@code println}, whichever fits your output pipeline.
 *
 * <p>Unlike {@link ConsoleSink}, this never moves the cursor: one frame is one
 * write. It is the right choice when the host application owns the stream,
 * e.g. a redirected console, a test harness, or a custom writer.</p>
 */
public final class PrintStreamSink implements ProgressSink {

    private final PrintStream out;
    private final boolean newline;

    /**
     * Creates a sink that appends a newline after each frame.
     *
     * @param out the stream to write to
     */
    public PrintStreamSink(PrintStream out) {
        this(out, true);
    }

    /**
     * Creates a sink with explicit newline behaviour.
     *
     * @param out the stream to write to
     * @param newline true to append a newline after each frame
     * @throws IllegalArgumentException if {@code out} is null
     */
    public PrintStreamSink(PrintStream out, boolean newline) {
        if (out == null) {
            throw new IllegalArgumentException("stream must not be null");
        }
        this.out = out;
        this.newline = newline;
    }

    /** {@code System.out.print(frame)} style: no automatic line break. */
    public static PrintStreamSink inline() {
        return new PrintStreamSink(System.out, false);
    }

    /**
     * Creates a sink equivalent to {@code out.print(frame)}.
     *
     * @param out the stream to write to
     * @return the configured sink
     */
    public static PrintStreamSink inline(PrintStream out) {
        return new PrintStreamSink(out, false);
    }

    /** {@code System.out.println(frame)} style: one line per frame. */
    public static PrintStreamSink lines(PrintStream out) {
        return new PrintStreamSink(out, true);
    }

    @Override
    public void write(String frame) {
        if (frame == null) {
            return;
        }
        if (newline) {
            out.println(frame);
        } else {
            out.print(frame);
        }
        out.flush();
    }

    @Override
    public void flush() {
        out.flush();
    }

    @Override
    public void close() {
        flush();
    }
}
