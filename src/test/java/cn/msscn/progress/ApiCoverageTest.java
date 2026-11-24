package cn.msscn.progress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.msscn.progress.TestSupport.check;
import static cn.msscn.progress.TestSupport.checkEquals;
import static cn.msscn.progress.TestSupport.tempFile;

/** Positive-path coverage of every public API and its overloads. */
public final class ApiCoverageTest {

    private static final Executor DIRECT = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    public static void runAll() throws Exception {
        factoriesAndFormatting();
        streamOverloads();
        transferAndCopy();
        runnerOverloads();
        groupOverloads();
        configAndSinks();
        indeterminateAndReporter();
    }

    private static ProgressConfig silentConfig() {
        return ProgressConfig.builder()
                .sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS)
                .build();
    }

    private static void factoriesAndFormatting() {
        ProgressBar plain = ProgressBar.of("plain");
        checkEquals(100.0, plain.getTotal(), "of(name) default total");
        plain.close();

        ProgressBar configured = ProgressBar.of("cfg", 8.0, silentConfig());
        configured.tick();
        checkEquals(1.0, configured.getValue(), "tick advances one unit");
        check(configured.render().contains("12.50%"), "of(name,total,config) renders");
        configured.close();

        ProgressBar withConfig = ProgressBar.of("cfg2", silentConfig());
        checkEquals(100.0, withConfig.getTotal(), "of(name,config) default total");
        withConfig.close();

        ProgressBar unknown = ProgressBar.unknown("stream", silentConfig());
        check(unknown.isIndeterminate() && unknown.getStatus() == ProgressStatus.PENDING,
                "unknown(name,config) factory");
        unknown.close();

        ProgressBar unknownPlain = ProgressBar.unknown("stream2");
        check(unknownPlain.isIndeterminate(), "unknown(name) factory");
        unknownPlain.close();

        checkEquals("1.5 KB", ByteFormat.format(1536L), "ByteFormat.format(long)");
        checkEquals("1.5 KB", ByteFormat.format(1536.0), "ByteFormat.format(double)");
        checkEquals("2.0 KB/s", ByteFormat.rate(2048.0), "ByteFormat.rate(double)");
        checkEquals("2.0 KB/s", ByteFormat.rate(2048L), "ByteFormat.rate(long)");

        ProgressSnapshot snapshot = new ProgressSnapshot(1, 1, "x", 50, 100, 50, 0,
                ProgressStatus.RUNNING, 0, 0, false);
        checkEquals(ByteFormat.format(snapshot.getValue()), ByteFormat.value(snapshot),
                "ByteFormat.value(snapshot)");
        check(ByteFormat.renderBytes(snapshot).contains("50 B / 100 B"), "renderBytes(snapshot)");

        check(ProgressFormat.format(snapshot, ProgressStyle.ASCII, 10, 2)
                .contains("[====>     ]"), "ProgressFormat.format(snapshot,style,width,decimals)");
        check(ProgressFormat.format(snapshot, silentConfig()).contains("50.00%"),
                "ProgressFormat.format(snapshot,config)");
    }

    private static void streamOverloads() throws Exception {
        byte[] payload = "hello progress bars".getBytes("UTF-8");
        File file = tempFile("read", payload);

        ProgressBar fromFile = ProgressBar.builder().name("file").config(silentConfig()).build();
        InputStream in = ProgressStreams.read(file, fromFile);
        byte[] bytes = ProgressStreams.readAllBytes(in, fromFile);
        check(Arrays.equals(payload, bytes), "read(File) + readAllBytes content");
        checkEquals((double) payload.length, fromFile.getValue(), "read(File) progress value");
        checkEquals((double) payload.length, fromFile.getTotal(), "read(File) total from length");
        check(fromFile.isDone(), "readAllBytes completes reporter");

        ProgressBar fromPath = ProgressBar.builder().name("path").config(silentConfig()).build();
        InputStream pin = ProgressStreams.read(file.toPath(), fromPath);
        while (pin.read() >= 0) {
            // drain one byte at a time
        }
        pin.close();
        checkEquals((double) payload.length, fromPath.getValue(), "read(Path) progress");

        ProgressBar fromString = ProgressBar.builder().name("str").config(silentConfig()).build();
        InputStream sin = ProgressStreams.read(file.getAbsolutePath(), fromString);
        byte[] buffer = new byte[4];
        while (sin.read(buffer) > 0) {
            // drain in chunks
        }
        sin.close();
        checkEquals((double) payload.length, fromString.getValue(), "read(String) progress");

        ProgressBar fromBytes = ProgressBar.builder().name("bytes").config(silentConfig()).build();
        InputStream bin = ProgressStreams.read(payload, fromBytes);
        while (bin.read() >= 0) {
            // drain
        }
        bin.close();
        checkEquals((double) payload.length, fromBytes.getValue(), "read(byte[]) progress");

        File target = File.createTempFile("pb-test-write", ".bin");
        target.deleteOnExit();
        ProgressBar written = ProgressBar.builder().name("write").total(payload.length).config(silentConfig()).build();
        OutputStream out = ProgressStreams.write(target, written);
        out.write(payload);
        out.close();
        check(Arrays.equals(payload, Files.readAllBytes(target.toPath())), "write(File) content");
        check(written.isDone(), "write(File) completes on close");

        File targetPath = File.createTempFile("pb-test-writepath", ".bin");
        targetPath.deleteOnExit();
        ProgressBar byPath = ProgressBar.builder().name("wp").total(payload.length).config(silentConfig()).build();
        OutputStream pout = ProgressStreams.write(targetPath.toPath(), byPath);
        pout.write(payload, 0, payload.length);
        pout.close();
        checkEquals((double) payload.length, byPath.getValue(), "write(Path) progress");

        File targetString = File.createTempFile("pb-test-writestring", ".bin");
        targetString.deleteOnExit();
        ProgressBar byString = ProgressBar.builder().name("ws").total(payload.length).config(silentConfig()).build();
        OutputStream sout = ProgressStreams.write(targetString.getAbsolutePath(), byString);
        sout.write(payload[0]);
        for (int i = 1; i < payload.length; i++) {
            sout.write(payload, i, 1);
        }
        sout.close();
        checkEquals((double) payload.length, byString.getValue(), "write(String) progress");

        ProgressBar noEof = ProgressBar.builder().name("noeof").config(silentConfig()).build();
        InputStream tracked = ProgressStreams.wrap(new ByteArrayInputStream(payload), noEof, false);
        while (tracked.read() >= 0) {
            // drain
        }
        check(noEof.getStatus() == ProgressStatus.RUNNING && noEof.getValue() == payload.length,
                "completeOnEof=false keeps RUNNING");
    }

    private static void transferAndCopy() throws Exception {
        byte[] payload = new byte[5000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        ProgressBar transferBar = ProgressBar.builder().name("transfer")
                .config(silentConfig()).build();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        long copied = ProgressStreams.transfer(new ByteArrayInputStream(payload), sink, transferBar, 512);
        checkEquals((long) payload.length, copied, "transfer returns byte count");
        check(Arrays.equals(payload, sink.toByteArray()), "transfer content");
        check(transferBar.isDone(), "transfer completes reporter");

        ProgressBar transferDefault = ProgressBar.builder().name("transfer2").total(payload.length)
                .config(silentConfig()).build();
        ByteArrayOutputStream sink2 = new ByteArrayOutputStream();
        ProgressStreams.transfer(new ByteArrayInputStream(payload), sink2, transferDefault);
        checkEquals((double) payload.length, transferDefault.getValue(), "transfer default buffer");

        File source = tempFile("copy-src", payload);
        File destination = File.createTempFile("pb-test-copy-dst", ".bin");
        destination.deleteOnExit();
        ProgressBar copyBar = ProgressBar.builder().name("copy").config(silentConfig()).build();
        long n = ProgressStreams.copy(source, destination, copyBar);
        checkEquals((long) payload.length, n, "copy(File,File) byte count");
        check(Arrays.equals(payload, Files.readAllBytes(destination.toPath())), "copy content");
        check(copyBar.isDone(), "copy completes reporter");

        File destination2 = File.createTempFile("pb-test-copy2", ".bin");
        destination2.deleteOnExit();
        ProgressBar copyPathBar = ProgressBar.builder().name("copyp").config(silentConfig()).build();
        ProgressStreams.copy(source.toPath(), destination2.toPath(), copyPathBar);
        checkEquals((double) payload.length, copyPathBar.getValue(), "copy(Path,Path)");

        File destination3 = File.createTempFile("pb-test-copy3", ".bin");
        destination3.deleteOnExit();
        ProgressBar copyStrBar = ProgressBar.builder().name("copys").config(silentConfig()).build();
        ProgressStreams.copy(source.getAbsolutePath(), destination3.getAbsolutePath(), copyStrBar);
        checkEquals((double) payload.length, copyStrBar.getValue(), "copy(String,String)");
    }

    private static void runnerOverloads() throws Exception {
        ProgressBar checked = ProgressBar.builder().name("checked").total(4).config(silentConfig()).build();
        final AtomicInteger sum = new AtomicInteger();
        ProgressRunner.run(checked, 4, new ProgressRunner.Step() {
            @Override
            public void execute(int index) throws Exception {
                sum.addAndGet(index);
            }
        });
        checkEquals(10, sum.get(), "run(Step) indices");
        check(checked.isDone() && checked.getValue() == 4.0, "run(Step) completes at steps");

        ProgressBar unchecked = ProgressBar.builder().name("unchecked").config(silentConfig()).build();
        ProgressRunner.runUnchecked(unchecked, 3, new ProgressRunner.UncheckedStep() {
            @Override
            public void execute(int index) {
            }
        });
        check(unchecked.isDone(), "runUnchecked completes");

        ProgressBar indexed = ProgressBar.builder().name("indexed").config(silentConfig()).build();
        final AtomicInteger indexSum = new AtomicInteger();
        List<String> items = Arrays.asList("a", "b", "c");
        ProgressRunner.forEach(indexed, items, new ProgressRunner.IndexedConsumer<String>() {
            @Override
            public void accept(String item, int index) {
                indexSum.addAndGet(index);
            }
        });
        checkEquals(6, indexSum.get(), "forEach(IndexedConsumer) indices");
        checkEquals(3.0, indexed.getTotal(), "forEach sets total from collection");
        check(indexed.isDone(), "forEach completes");

        ProgressBar simple = ProgressBar.builder().name("simple").config(silentConfig()).build();
        final AtomicInteger seen = new AtomicInteger();
        ProgressRunner.forEach(simple, items, new java.util.function.Consumer<String>() {
            @Override
            public void accept(String item) {
                seen.incrementAndGet();
            }
        });
        checkEquals(3, seen.get(), "forEach(Consumer)");

        ProgressBar tasksBar = ProgressBar.builder().name("tasks").config(silentConfig()).build();
        final AtomicInteger ran = new AtomicInteger();
        List<Runnable> tasks = new ArrayList<Runnable>();
        for (int i = 0; i < 3; i++) {
            tasks.add(new Runnable() {
                @Override
                public void run() {
                    ran.incrementAndGet();
                }
            });
        }
        ProgressRunner.runTasks(tasksBar, tasks);
        checkEquals(3, ran.get(), "runTasks");
        check(tasksBar.isDone(), "runTasks completes");
    }

    private static void groupOverloads() throws Exception {
        ProgressConfig config = ProgressConfig.builder()
                .sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS)
                .build();
        ProgressGroup group = ProgressGroup.of(config);

        ProgressBar defaultBar = group.add("default");
        checkEquals(100.0, defaultBar.getTotal(), "group.add(name) default total");

        File file = tempFile("groupfile", new byte[64]);
        ProgressBar fileBar = group.addFile(file);
        checkEquals(64.0, fileBar.getTotal(), "group.addFile(File) uses length");
        check(fileBar.getConfig().getUnit().equals("B"), "group.addFile sets B unit");

        ProgressBar pathBar = group.addFile(file.toPath());
        checkEquals(64.0, pathBar.getTotal(), "group.addFile(Path)");

        ProgressBar unknown = group.addUnknown("chunked");
        check(unknown.isIndeterminate(), "group.addUnknown(name)");

        ProgressBar customUnknown = group.addUnknown(
                "chunked2", ProgressConfig.builder().width(8).style(ProgressStyle.HASH).build());
        checkEquals(8, customUnknown.getConfig().getWidth(), "group.addUnknown(name,config)");

        checkEquals(5, group.size(), "group size with all overloads");
        check(group.render().split("\n").length == 5, "group renders five lines");
        check(group.render().contains("[1/5]") && group.render().contains("[5/5]"),
                "group indices span count");

        defaultBar.complete();
        fileBar.complete();
        pathBar.complete();
        unknown.complete();
        customUnknown.complete();
        checkEquals(5, group.allDone().get(1, TimeUnit.SECONDS).size(), "group allDone overloads");
        group.close();

        File empty = File.createTempFile("pb-test-empty", ".bin");
        empty.deleteOnExit();
        ProgressGroup g2 = ProgressGroup.of(config);
        ProgressBar emptyBar = g2.addFile(empty);
        check(emptyBar.isIndeterminate(), "zero-length file becomes unknown total");
        emptyBar.complete();
        check(g2.allDone().get(1, TimeUnit.SECONDS).size() == 1, "zero-length file group completes");
        g2.close();
    }

    private static void configAndSinks() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(bytes, true, "UTF-8");
        ProgressConfig streamConfig = ProgressConfig.builder()
                .sink(stream)
                .refreshInterval(0L)
                .integer()
                .build();
        check(streamConfig.getSink() instanceof ConsoleSink, "sink(PrintStream) shorthand");
        checkEquals(0L, streamConfig.getRefreshIntervalMillis(), "refreshInterval(long) shorthand");
        checkEquals(5L, ProgressConfig.builder().refreshInterval(5L).build()
                .getRefreshIntervalMillis(), "refreshInterval(long) non-zero");
        checkEquals(0, streamConfig.getDecimals(), "integer() shorthand");

        ProgressBar bar = ProgressBar.builder().name("sink").total(2).config(streamConfig).build();
        bar.update(1);
        bar.flush();
        bar.update(2);
        bar.close();
        String printed = TestSupport.readUtf8(bytes);
        check(printed.contains("50%") && printed.contains("100%"), "plain console sink writes frames");
        check(!printed.contains("\u001b["), "plain console sink has no ANSI codes");

        TestSupport.CapturingSink capturing = new TestSupport.CapturingSink();
        TestSupport.CapturingLogger logger = new TestSupport.CapturingLogger();
        CompositeSink composite = new CompositeSink(capturing, new LoggingSink(logger, "LOG "));
        ProgressConfig compositeConfig = ProgressConfig.builder().sink(composite).build();
        ProgressBar cb = ProgressBar.builder().name("combo").total(4).config(compositeConfig).build();
        cb.update(4);
        check(capturing.writes.get() >= 1, "CompositeSink writes to sink");
        check(!logger.messages.isEmpty() && logger.messages.get(0).startsWith("LOG "),
                "CompositeSink writes to logger with marker");
        cb.close();
        composite.close();
        checkEquals(1, capturing.closes.get(), "CompositeSink.close propagates");

        ByteArrayOutputStream ansiBytes = new ByteArrayOutputStream();
        PrintStream ansiStream = new PrintStream(ansiBytes, true, "UTF-8");
        ConsoleSink interactive = ConsoleSink.interactive(ansiStream);
        interactive.write("[1/1] a [====] 50%");
        interactive.write("[1/1] a [====] 100%");
        String ansi = TestSupport.readUtf8(ansiBytes);
        check(ansi.contains("\u001b["), "interactive console emits cursor-up");
        check(ansi.contains("\u001b[K"), "interactive console clears line");
        interactive.close();

        ProgressConfig loggerConfig = ProgressConfig.builder().logger(logger).build();
        check(loggerConfig.getSink() instanceof LoggingSink, "logger(ProgressLogger) shorthand");

        final AtomicInteger callbacks = new AtomicInteger();
        ProgressListener listener = new ProgressListener() {
            @Override
            public void onComplete(ProgressSnapshot snapshot) {
                callbacks.incrementAndGet();
            }
        };
        ProgressConfig callbackConfig = ProgressConfig.builder()
                .sink(NoopSink.INSTANCE)
                .callback(listener, DIRECT)
                .build();
        ProgressBar callbackBar = ProgressBar.builder().name("cb").total(1).config(callbackConfig).build();
        callbackBar.complete();
        checkEquals(1, callbacks.get(), "callback(listener,executor) shorthand");
        callbackBar.close();
    }

    private static void indeterminateAndReporter() throws Exception {
        ProgressConfig config = silentConfig();
        ProgressBar bar = ProgressBar.unknown("spinner", config);
        bar.update(1234.5);
        String line = bar.render();
        check(line.contains("--") && !line.contains("--%"), "spinner renders no percent sign");
        check(line.contains("{") == false, "no unresolved placeholders");

        char[] frames = new char[40];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = ProgressStyle.spinnerFrame(i * 100000000L);
        }
        check(new String(frames).indexOf('|') >= 0 && new String(frames).indexOf('\\') >= 0,
                "spinner cycles through frames");

        bar.setTotal(2000);
        check(!bar.isIndeterminate(), "setTotal converts to determinate");
        check(Math.abs(bar.getPercent() - 61.725) < 0.001, "percent after setTotal");
        bar.complete();
        checkEquals(100.0, bar.getPercent(), "complete after setTotal");
        bar.close();

        ProgressBar cancelled = ProgressBar.unknown("c", config);
        cancelled.update(1);
        cancelled.cancel();
        check(cancelled.isDone() && cancelled.getStatus() == ProgressStatus.CANCELLED,
                "cancel indeterminate bar");
        check(cancelled.snapshot().isIndeterminate(), "cancelled snapshot keeps indeterminate flag");
        cancelled.close();

        ProgressReporter noop = ProgressReporter.noop();
        noop.start().update(5).increment(2).setTotal(10).complete().cancel();
        check(!noop.isDone() && noop.getPercent() == 0.0, "ProgressReporter.noop behaviour");
        checkEquals("noop", noop.snapshot().getName(), "noop snapshot name");

        ProgressConfig config2 = silentConfig();
        final AtomicReference<ProgressSnapshot> finished = new AtomicReference<ProgressSnapshot>();
        ProgressBar futureBar = ProgressBar.builder().name("future").total(1).config(config2).build();
        futureBar.future().thenAccept(finished::set);
        futureBar.complete();
        check(finished.get() != null, "CompletableFuture callback fires");
        futureBar.close();
    }
}
