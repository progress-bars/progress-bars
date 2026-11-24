package cn.msscn.progress;

/** Where a rendered frame goes. Frames may contain multiple '\n'-separated lines. */
public interface ProgressSink extends AutoCloseable {

    /**
     * Writes one rendered frame, which may contain multiple lines.
     *
     * @param frame the rendered frame
     */
    void write(String frame);

    @Override
    void close();

    /** Optional flush hook, used when a bar/group is closed. */
    default void flush() {
    }
}
