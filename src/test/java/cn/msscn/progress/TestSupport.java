package cn.msscn.progress;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Shared assertion helpers and recording sinks for the dependency-free test suites. */
public final class TestSupport {

    public static int checks;

    private TestSupport() {
    }

    public static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError("FAILED: " + message);
        }
    }

    public static void checkEquals(Object expected, Object actual, String message) {
        check(expected == null ? actual == null : expected.equals(actual),
                message + " (expected=" + expected + ", actual=" + actual + ")");
    }

    public static <T extends Throwable> T expect(Class<T> type, ThrowingRunnable action) {
        checks++;
        try {
            action.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
            throw new AssertionError("FAILED: expected " + type.getName() + " but got " + t, t);
        }
        throw new AssertionError("FAILED: expected " + type.getName() + " but nothing was thrown");
    }

    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static final class CapturingSink implements ProgressSink {
        public final AtomicInteger writes = new AtomicInteger();
        public final AtomicInteger closes = new AtomicInteger();
        public final AtomicReference<String> lastFrame = new AtomicReference<String>();
        public final List<String> frames = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public void write(String frame) {
            writes.incrementAndGet();
            lastFrame.set(frame);
            frames.add(frame);
        }

        @Override
        public void close() {
            closes.incrementAndGet();
        }
    }

    public static final class CapturingLogger implements ProgressLogger {
        public final List<String> messages = Collections.synchronizedList(new ArrayList<String>());

        @Override
        public void log(String message) {
            messages.add(message);
        }
    }

    public static File tempFile(String name, byte[] content) throws IOException {
        File file = Files.createTempFile("pb-test-" + name, ".bin").toFile();
        file.deleteOnExit();
        Files.write(file.toPath(), content);
        return file;
    }

    public static String readUtf8(java.io.ByteArrayOutputStream out) throws IOException {
        return out.toString(Charset.forName("UTF-8").name());
    }
}
