package cn.msscn.progress;

import java.io.PrintStream;

/**
 * Console output.
 *
 * <p>In an interactive terminal (detected via {@code System.console() != null})
 * frames overwrite the previous ones in place using ANSI cursor movement, like
 * wget/docker. In a redirected/piped stream it falls back to one frame per
 * block of lines so log files stay readable.</p>
 */
public final class ConsoleSink implements ProgressSink {

    private final PrintStream out;
    private final boolean interactive;
    private int lastLines;

    /**
     * Creates a sink writing to {@code System.out}, using in-place rendering only
     * when a console is attached.
     */
    public ConsoleSink() {
        this(System.out, System.console() != null);
    }

    /**
     * Creates a sink with an explicit output stream and rendering mode.
     *
     * @param out the stream to write to
     * @param interactive true to overwrite frames in place, false to append them
     * @throws IllegalArgumentException if {@code out} is null
     */
    public ConsoleSink(PrintStream out, boolean interactive) {
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        this.out = out;
        this.interactive = interactive;
    }

    /**
     * Creates a sink that auto-detects whether the output is interactive.
     *
     * @return a sink writing to {@code System.out}
     */
    public static ConsoleSink auto() {
        return new ConsoleSink();
    }

    /**
     * Creates a sink that always overwrites frames in place.
     *
     * @param out the stream to write to
     * @return the configured sink
     */
    public static ConsoleSink interactive(PrintStream out) {
        return new ConsoleSink(out, true);
    }

    /** Plain mode: append every frame instead of overwriting (good for files/pipes). */
    public static ConsoleSink plain(PrintStream out) {
        return new ConsoleSink(out, false);
    }

    /**
     * Creates a sink writing to {@code System.err} with auto-detected mode.
     *
     * @return the configured sink
     */
    public static ConsoleSink stderr() {
        return new ConsoleSink(System.err, System.console() != null);
    }

    /**
     * Reports whether frames are overwritten in place.
     *
     * @return true for in-place rendering, false for append mode
     */
    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public synchronized void write(String frame) {
        if (frame == null) {
            return;
        }
        String[] lines = frame.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("\r")) {
                lines[i] = lines[i].substring(0, lines[i].length() - 1);
            }
        }

        if (!interactive) {
            for (String line : lines) {
                out.println(line);
            }
            out.flush();
            return;
        }

        // Cursor sits just below the previous frame; walk back to its first line.
        if (lastLines > 0) {
            out.print("\u001b[" + lastLines + "A");
        }
        for (int i = 0; i < lines.length; i++) {
            out.print("\r" + lines[i] + "\u001b[K");
            if (i < lines.length - 1) {
                out.print('\n');
            }
        }
        // Erase lines left over from a previous, taller frame.
        for (int i = lines.length; i < lastLines; i++) {
            out.print("\n\r\u001b[K");
        }
        out.print('\n');
        lastLines = lines.length;
        out.flush();
    }

    @Override
    public synchronized void flush() {
        out.flush();
    }

    @Override
    public void close() {
        flush();
    }
}
