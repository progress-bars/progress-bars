package cn.msscn.progress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static cn.msscn.progress.TestSupport.check;
import static cn.msscn.progress.TestSupport.checkEquals;
import static cn.msscn.progress.TestSupport.expect;
import static cn.msscn.progress.TestSupport.tempFile;

/** Negative paths, invalid arguments, failure propagation and lifecycle edge cases. */
public final class ExceptionTest {

    public static void runAll() throws Exception {
        invalidConfiguration();
        invalidStreams();
        invalidRunner();
        invalidGroup();
        lifecycleEdges();
        failurePropagation();
    }

    private static ProgressConfig silent() {
        return ProgressConfig.builder().sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS).build();
    }

    private static void invalidConfiguration() {
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressBar.builder().name(null).build(); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressBar.of("x", 0.0); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressBar.of("x", -1.0); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressBar.of("x", Double.NaN); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressBar.of("x", Double.POSITIVE_INFINITY); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().width(0); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().decimals(-1); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().decimals(7); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().refreshIntervalMillis(-1); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().refreshInterval(1, null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().style(null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().template(null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().sink((ProgressSink) null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().listenerExecutor(null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressConfig.builder().sink((java.io.PrintStream) null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { new LoggingSink(null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { new CompositeSink(); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { new ConsoleSink(null, false); }
        });

        ProgressBar bar = ProgressBar.of("x", 10, silent());
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { bar.setTotal(0); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { bar.setTotal(Double.NaN); }
        });
        bar.close();
    }

    private static void invalidStreams() throws Exception {
        final ProgressReporter reporter = ProgressReporter.noop();
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressStreams.wrap((InputStream) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressStreams.wrap(new ByteArrayInputStream(new byte[1]), null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressStreams.wrap((OutputStream) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressStreams.read((File) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressStreams.read((String) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressStreams.read((byte[]) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressStreams.write((File) null, reporter); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                try { ProgressStreams.transfer(null, new ByteArrayOutputStream(), reporter); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                try {
                    ProgressStreams.transfer(new ByteArrayInputStream(new byte[1]), null, reporter);
                } catch (IOException e) { throw new RuntimeException(e); }
            }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                try {
                    ProgressStreams.transfer(new ByteArrayInputStream(new byte[1]),
                            new ByteArrayOutputStream(), reporter, 0);
                } catch (IOException e) { throw new RuntimeException(e); }
            }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                try { ProgressStreams.copy((File) null, new File("x"), reporter); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                try { ProgressStreams.readAllBytes(null, reporter); }
                catch (IOException e) { throw new RuntimeException(e); }
            }
        });

        File missing = new File(System.getProperty("java.io.tmpdir"), "pb-does-not-exist-" + System.nanoTime());
        expect(IOException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception {
                ProgressStreams.read(missing, ProgressReporter.noop());
            }
        });

        File source = tempFile("src", new byte[]{1, 2, 3});
        ProgressBar bar = ProgressBar.builder().name("x").total(3).config(silent()).build();
        InputStream in = ProgressStreams.read(source, bar);
        while (in.read() >= 0) {
            // drain to EOF so the reporter completes
        }
        in.close();
        in.close(); // second close must be harmless
        check(bar.isDone(), "double close keeps completed state");
    }

    private static void invalidRunner() {
        final ProgressReporter reporter = ProgressReporter.noop();
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressRunner.run(null, 1, new ProgressRunner.Step() {
                @Override public void execute(int index) { }
            }); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressRunner.run(reporter, 0, new ProgressRunner.Step() {
                @Override public void execute(int index) { }
            }); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressRunner.run(reporter, -1, new ProgressRunner.Step() {
                @Override public void execute(int index) { }
            }); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception { ProgressRunner.run(reporter, 1, null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressRunner.forEach(reporter, null,
                    (java.util.function.Consumer<String>) s -> { }); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { ProgressRunner.forEach(reporter, Arrays.asList("a"),
                    (java.util.function.Consumer<String>) null); }
        });

        ProgressBar failing = ProgressBar.builder().name("fail").total(3).config(silent()).build();
        IllegalStateException thrown = expect(IllegalStateException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception {
                try {
                    ProgressRunner.run(failing, 3, new ProgressRunner.Step() {
                        @Override public void execute(int index) throws Exception {
                            if (index == 2) {
                                throw new IllegalStateException("boom");
                            }
                        }
                    });
                } catch (Exception e) {
                    throw e;
                }
            }
        });
        checkEquals("boom", thrown.getMessage(), "runner rethrows original exception");
        check(failing.getStatus() == ProgressStatus.CANCELLED, "runner cancels on failure");
        checkEquals(1.0, failing.getValue(), "runner stops at failing step");

        ProgressBar failingForEach = ProgressBar.builder().name("f2").config(silent()).build();
        expect(RuntimeException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressRunner.forEach(failingForEach, Arrays.asList(1, 2, 3),
                        (java.util.function.Consumer<Integer>) i -> {
                            if (i == 2) {
                                throw new RuntimeException("stop");
                            }
                        });
            }
        });
        check(failingForEach.getStatus() == ProgressStatus.CANCELLED, "forEach cancels on exception");
    }

    private static void invalidGroup() {
        final ProgressGroup group = ProgressGroup.of(silent());
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { group.addFile((File) null); }
        });
        expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() { group.add(null, 1.0); }
        });
        checkEquals(0, group.size(), "failed add does not change group size");
        checkEquals("", group.render(), "empty group renders empty string");
        group.close();
        group.close(); // idempotent
    }

    private static void lifecycleEdges() throws Exception {
        ProgressBar bar = ProgressBar.builder().name("life").total(10).config(silent()).build();
        bar.update(5);
        bar.complete();
        bar.update(7);
        bar.increment(100);
        bar.setTotal(20);
        bar.complete();
        bar.cancel();
        checkEquals(10.0, bar.getValue(), "updates after completion are ignored");
        check(bar.getStatus() == ProgressStatus.COMPLETED, "complete wins over cancel");
        bar.flush();
        bar.close();
        bar.close();
        check(bar.future().isDone(), "future completed exactly once");

        ProgressBar c = ProgressBar.builder().name("c").total(5).config(silent()).build();
        c.cancel();
        c.complete();
        check(c.getStatus() == ProgressStatus.CANCELLED, "cancel wins over complete");
        c.close();

        ProgressBar nan = ProgressBar.builder().name("nan").total(5).config(silent()).build();
        nan.update(2);
        nan.update(Double.NaN);
        checkEquals(2.0, nan.getValue(), "NaN update is ignored");
        nan.increment(Double.NaN);
        checkEquals(2.0, nan.getValue(), "NaN increment is ignored");
        nan.update(-100);
        checkEquals(0.0, nan.getValue(), "negative update clamps to zero");
        nan.close();
    }

    private static void failurePropagation() throws Exception {
        final ProgressBar listenerBar = ProgressBar.builder().name("listener")
                .total(2)
                .config(ProgressConfig.builder()
                        .sink(NoopSink.INSTANCE)
                        .refreshInterval(0, TimeUnit.MILLISECONDS)
                        .listener(new ProgressListener() {
                            @Override
                            public void onProgress(ProgressSnapshot snapshot) {
                                throw new RuntimeException("listener failure");
                            }

                            @Override
                            public void onComplete(ProgressSnapshot snapshot) {
                                throw new RuntimeException("listener failure");
                            }
                        })
                        .listenerExecutor(Runnable::run)
                        .build())
                .build();
        listenerBar.update(1);
        listenerBar.complete();
        check(listenerBar.isDone(), "throwing listener does not break progress");

        Executor rejecting = new Executor() {
            @Override
            public void execute(Runnable command) {
                throw new IllegalStateException("rejected");
            }
        };
        ProgressBar rejectedBar = ProgressBar.builder().name("rejected").total(1)
                .config(ProgressConfig.builder().sink(NoopSink.INSTANCE)
                        .listener(new ProgressListener() { })
                        .listenerExecutor(rejecting).build())
                .build();
        rejectedBar.complete();
        check(rejectedBar.isDone(), "rejecting executor does not break progress");
        rejectedBar.close();

        final ProgressBar ioBar = ProgressBar.builder().name("io").total(10)
                .config(silent()).build();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk failure");
            }
        };
        expect(IOException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() throws Exception {
                ProgressStreams.transfer(broken, new ByteArrayOutputStream(), ioBar);
            }
        });
        check(ioBar.getStatus() == ProgressStatus.CANCELLED, "transfer cancels reporter on IOException");

        List<String> names = new ArrayList<String>();
        names.add("dup");
        names.add("dup");
        ProgressGroup group = ProgressGroup.of(silent());
        group.add(names.get(0), 1);
        group.add(names.get(1), 1);
        checkEquals(2, group.size(), "duplicate names are allowed");
        group.close();
    }
}
