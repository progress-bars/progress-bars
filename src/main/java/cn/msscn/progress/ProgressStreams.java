package cn.msscn.progress;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Decorators and one-liners that turn byte sources into progress sources.
 *
 * <p>Accepted inputs: {@link InputStream}, {@link OutputStream}, {@link File},
 * {@link Path}, path strings and {@code byte[]}. The reporter receives cumulative
 * bytes; for known sizes the total is set automatically. For unknown sizes use
 * {@link ProgressBar.Builder#unknownTotal()}.</p>
 */
public final class ProgressStreams {

    private static final int DEFAULT_BUFFER = 8192;

    private ProgressStreams() {
    }

    // ------------------------------------------------------------------ wrap

    /**
     * Wraps an input stream, completing the reporter on end of stream.
     *
     * @param in the source stream to decorate
     * @param reporter the reporter receiving cumulative byte counts
     * @return the decorated stream
     */
    public static InputStream wrap(InputStream in, ProgressReporter reporter) {
        return wrap(in, reporter, true);
    }

    /**
     * Wraps an input stream so each byte read advances the reporter.
     *
     * @param in the source stream to decorate
     * @param reporter the reporter receiving cumulative byte counts
     * @param completeOnEof whether to mark the reporter complete on end of stream
     * @return the decorated stream
     */
    public static InputStream wrap(InputStream in, ProgressReporter reporter, boolean completeOnEof) {
        require(in, reporter);
        return new ProgressInputStream(in, reporter, completeOnEof);
    }

    /**
     * Wraps an output stream, completing the reporter when the stream is closed.
     *
     * @param out the target stream to decorate
     * @param reporter the reporter receiving cumulative byte counts
     * @return the decorated stream
     */
    public static OutputStream wrap(OutputStream out, ProgressReporter reporter) {
        return wrap(out, reporter, true);
    }

    /**
     * Wraps an output stream so each byte written advances the reporter.
     *
     * @param out the target stream to decorate
     * @param reporter the reporter receiving cumulative byte counts
     * @param completeOnClose whether to mark the reporter complete when the stream is closed
     * @return the decorated stream
     */
    public static OutputStream wrap(OutputStream out, ProgressReporter reporter, boolean completeOnClose) {
        require(out, reporter);
        return new ProgressOutputStream(out, reporter, completeOnClose);
    }

    // --------------------------------------------------------------- read()

    /** Opens a file for reading and sets the reporter total to its length. */
    public static InputStream read(File file, ProgressReporter reporter) throws IOException {
        require(file, reporter);
        reporter.setTotal(file.length());
        return wrap(new FileInputStream(file), reporter);
    }

    /**
     * Opens a file for reading and sets the reporter total to its length.
     *
     * @param path the file to read
     * @param reporter the reporter to update
     * @return a tracked stream; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static InputStream read(Path path, ProgressReporter reporter) throws IOException {
        require(path, reporter);
        return read(path.toFile(), reporter);
    }

    /**
     * Opens a file by path string for reading, with total set to the file length.
     *
     * @param path the path of the file to read
     * @param reporter the reporter to update
     * @return a tracked stream; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static InputStream read(String path, ProgressReporter reporter) throws IOException {
        require(path, reporter);
        return read(new File(path), reporter);
    }

    /**
     * Wraps a byte array as a tracked input stream.
     *
     * @param data the bytes to expose as a stream
     * @param reporter the reporter to update
     * @return a tracked stream over the array
     */
    public static InputStream read(byte[] data, ProgressReporter reporter) {
        require(data, reporter);
        if (data.length > 0) {
            reporter.setTotal(data.length);
        }
        return wrap(new ByteArrayInputStream(data), reporter);
    }

    // -------------------------------------------------------------- write()

    /** Opens a file for writing; an existing file's length is used as total when non-zero. */
    public static OutputStream write(File file, ProgressReporter reporter) throws IOException {
        require(file, reporter);
        if (file.exists() && file.length() > 0) {
            reporter.setTotal(file.length());
        }
        return wrap(new FileOutputStream(file), reporter);
    }

    /**
     * Opens a file for writing; an existing non-zero length is used as the total.
     *
     * @param path the file to write
     * @param reporter the reporter to update
     * @return a tracked stream; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static OutputStream write(Path path, ProgressReporter reporter) throws IOException {
        require(path, reporter);
        return write(path.toFile(), reporter);
    }

    /**
     * Opens a file by path string for writing.
     *
     * @param path the path of the file to write
     * @param reporter the reporter to update
     * @return a tracked stream; the caller must close it
     * @throws IOException if the file cannot be opened
     */
    public static OutputStream write(String path, ProgressReporter reporter) throws IOException {
        require(path, reporter);
        return write(new File(path), reporter);
    }

    // ------------------------------------------------------------ transfer()

    /** Copies bytes from input to output while reporting read progress; completes on success. */
    public static long transfer(InputStream in, OutputStream out, ProgressReporter reporter) throws IOException {
        return transfer(in, out, reporter, DEFAULT_BUFFER);
    }

    /**
     * Copies bytes from an input stream to an output stream while reporting progress.
     *
     * @param in the source stream
     * @param out the target stream
     * @param reporter the reporter to update
     * @param bufferSize transfer buffer size in bytes; must be at least 1
     * @return the number of bytes copied
     * @throws IOException if a read or write fails
     * @throws IllegalArgumentException if an argument is null or the buffer is too small
     */
    public static long transfer(InputStream in, OutputStream out, ProgressReporter reporter,
                                int bufferSize) throws IOException {
        require(in, reporter);
        if (out == null) {
            throw new IllegalArgumentException("out must not be null");
        }
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be >= 1");
        }
        InputStream tracked = wrap(in, reporter, false);
        reporter.start();
        byte[] buffer = new byte[bufferSize];
        long total = 0L;
        try {
            int n;
            while ((n = tracked.read(buffer)) > 0) {
                out.write(buffer, 0, n);
                total += n;
            }
            reporter.complete();
            return total;
        } catch (IOException e) {
            reporter.cancel();
            throw e;
        }
    }

    /** File-to-file copy with progress; the total is taken from the source file length. */
    public static long copy(File source, File target, ProgressReporter reporter) throws IOException {
        require(source, reporter);
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        reporter.setTotal(source.length());
        InputStream in = new FileInputStream(source);
        OutputStream out = new FileOutputStream(target);
        try {
            return transfer(in, out, reporter);
        } finally {
            in.close();
            out.close();
        }
    }

    /**
     * Copies a file, taking the reporter total from the source length.
     *
     * @param source the source file
     * @param target the destination file
     * @param reporter the reporter to update
     * @return the number of bytes copied
     * @throws IOException if the copy fails
     */
    public static long copy(Path source, Path target, ProgressReporter reporter) throws IOException {
        require(source, reporter);
        require(target, reporter);
        return copy(source.toFile(), target.toFile(), reporter);
    }

    /**
     * Copies a file given by path strings.
     *
     * @param source the source path
     * @param target the destination path
     * @param reporter the reporter to update
     * @return the number of bytes copied
     * @throws IOException if the copy fails
     */
    public static long copy(String source, String target, ProgressReporter reporter) throws IOException {
        require(source, reporter);
        require(target, reporter);
        return copy(new File(source), new File(target), reporter);
    }

    /** Utility: read a whole stream into a byte array (still reports progress). */
    public static byte[] readAllBytes(InputStream in, ProgressReporter reporter) throws IOException {
        require(in, reporter);
        InputStream tracked = wrap(in, reporter, true);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[DEFAULT_BUFFER];
        int n;
        while ((n = tracked.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void require(Object value, ProgressReporter reporter) {
        if (value == null || reporter == null) {
            throw new IllegalArgumentException("source and reporter must not be null");
        }
    }

    private static final class ProgressInputStream extends FilterInputStream {

        private final ProgressReporter reporter;
        private final boolean completeOnEof;
        private long bytesRead;

        ProgressInputStream(InputStream in, ProgressReporter reporter, boolean completeOnEof) {
            super(in);
            this.reporter = reporter;
            this.completeOnEof = completeOnEof;
            reporter.start();
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                advance(1L);
            } else if (completeOnEof) {
                reporter.complete();
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = super.read(b, off, len);
            if (count > 0) {
                advance(count);
            } else if (count < 0 && completeOnEof) {
                reporter.complete();
            }
            return count;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            if (skipped > 0) {
                advance(skipped);
            }
            return skipped;
        }

        private void advance(long count) {
            bytesRead += count;
            reporter.update(bytesRead);
        }
    }

    private static final class ProgressOutputStream extends FilterOutputStream {

        private final ProgressReporter reporter;
        private final boolean completeOnClose;
        private long bytesWritten;

        ProgressOutputStream(OutputStream out, ProgressReporter reporter, boolean completeOnClose) {
            super(out);
            this.reporter = reporter;
            this.completeOnClose = completeOnClose;
            reporter.start();
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            advance(1L);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            advance(len);
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                if (completeOnClose) {
                    reporter.complete();
                }
            }
        }

        private void advance(long count) {
            bytesWritten += count;
            reporter.update(bytesWritten);
        }
    }
}
