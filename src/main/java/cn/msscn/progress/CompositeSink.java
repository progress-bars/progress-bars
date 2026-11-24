package cn.msscn.progress;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Fan-out sink: e.g. console + log at the same time. */
public final class CompositeSink implements ProgressSink {

    private final List<ProgressSink> sinks;

    /**
     * Creates a sink that forwards every frame to all given sinks in order.
     *
     * @param sinks one or more non-null sinks
     * @throws IllegalArgumentException if no sink is supplied or any sink is null
     */
    public CompositeSink(ProgressSink... sinks) {
        if (sinks == null || sinks.length == 0) {
            throw new IllegalArgumentException("at least one sink is required");
        }
        List<ProgressSink> copy = new ArrayList<ProgressSink>(Arrays.asList(sinks));
        for (ProgressSink sink : copy) {
            if (sink == null) {
                throw new IllegalArgumentException("sink must not be null");
            }
        }
        this.sinks = Collections.unmodifiableList(copy);
    }

    @Override
    public void write(String frame) {
        for (ProgressSink sink : sinks) {
            sink.write(frame);
        }
    }

    @Override
    public void flush() {
        for (ProgressSink sink : sinks) {
            sink.flush();
        }
    }

    @Override
    public void close() {
        for (ProgressSink sink : sinks) {
            sink.close();
        }
    }
}
