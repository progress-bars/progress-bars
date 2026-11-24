package cn.msscn.progress.demo;

import cn.msscn.progress.ByteFormat;
import cn.msscn.progress.CompositeSink;
import cn.msscn.progress.ConsoleSink;
import cn.msscn.progress.JdkProgressLogger;
import cn.msscn.progress.LoggingSink;
import cn.msscn.progress.NoopSink;
import cn.msscn.progress.ProgressConfig;
import cn.msscn.progress.ProgressFormat;
import cn.msscn.progress.ProgressGroup;
import cn.msscn.progress.ProgressBar;
import cn.msscn.progress.ProgressListener;
import cn.msscn.progress.ProgressReporter;
import cn.msscn.progress.ProgressRunner;
import cn.msscn.progress.ProgressSnapshot;
import cn.msscn.progress.ProgressStreams;
import cn.msscn.progress.ProgressStyle;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runnable showcase of every supported usage mode. */
public final class ProgressBarDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. single bar, 2 decimals, console ===");
        singleBar();

        System.out.println("=== 2. integer percent, bytes, detailed template ===");
        integerBytes();

        System.out.println("=== 3. group: 3 parallel downloads with [x/xx] prefix ===");
        group();

        System.out.println("=== 4. logging sink (console + java.util.logging) ===");
        logging();

        System.out.println("=== 5. pure API: no output at all ===");
        apiOnly();

        System.out.println("=== 6. manual refresh + async callback ===");
        manualAndAsync();

        System.out.println("=== 7. download/upload streams + byte formatting ===");
        streams();

        System.out.println("=== 8. execution steps + unknown total spinner ===");
        executionAndIndeterminate();

        System.out.println("=== 9. host integration: own logger, own formatter, own output ===");
        hostIntegration();

        System.out.println("=== done ===");
    }

    /** The library never owns the output: the host decides where frames go and how they look. */
    private static void hostIntegration() {
        ProgressConfig config = ProgressConfig.builder()
                .template("[{job}] {name} {bar} {percent}% {note}")
                .extra("job", "JOB-9")
                .extra("note", "injected by host app")
                .formatter((snapshot, cfg) -> "HOST " + ProgressFormat.format(snapshot, cfg))
                .logWith(message -> System.out.println("  app-log: " + message))
                .refreshInterval(0L)
                .build();

        ProgressBar bar = ProgressBar.of("payload.bin", 100, config);
        bar.start();
        bar.update(40);
        bar.flush();

        // Or render a one-off string and print/store it yourself, with no sink at all.
        String own = bar.render((snapshot, cfg) -> "custom=" + (long) snapshot.getPercent());
        System.out.println("  own-format: " + own);

        bar.complete();
        bar.close();
    }

    private static void singleBar() throws InterruptedException {
        ProgressConfig config = ProgressConfig.builder()
                .width(28)
                .twoDecimals()                                  // 66.67%
                .style(ProgressStyle.ASCII)
                .refreshInterval(80, TimeUnit.MILLISECONDS)     // <= 12.5 frames/s
                .sink(ConsoleSink.auto())
                .build();

        ProgressBar bar = ProgressBar.builder()
                .name("app.jar")
                .total(100)
                .config(config)
                .build();

        for (int i = 0; i <= 100; i += 5) {
            bar.update(i);
            sleep(20L);
        }
        bar.close();
        config.getSink().close();
    }

    private static void integerBytes() throws InterruptedException {
        ProgressConfig config = ProgressConfig.builder()
                .width(24)
                .integer()                                      // 67%
                .style(ProgressStyle.HASH)
                .unit("B")
                .template(ProgressFormat.DETAILED)
                .refreshIntervalMillis(120L)
                .sink(new ConsoleSink(System.out, false))       // append mode for this demo
                .build();

        ProgressBar bar = ProgressBar.builder()
                .name("payload.bin")
                .total(1024.0 * 1024.0)
                .config(config)
                .build();

        for (int i = 0; i < 16; i++) {
            bar.increment(64.0 * 1024.0);
            sleep(40L);
        }
        bar.close();
    }

    private static void group() throws Exception {
        Logger progressLogger = Logger.getLogger("demo.progress");
        progressLogger.setLevel(Level.INFO);

        ProgressConfig groupConfig = ProgressConfig.builder()
                .width(20)
                .twoDecimals()
                .style(ProgressStyle.BLOCK)
                .refreshInterval(100, TimeUnit.MILLISECONDS)
                .sink(ConsoleSink.auto())
                .listener(new ProgressListener() {
                    @Override
                    public void onComplete(ProgressSnapshot snapshot) {
                        System.out.println("[callback] group finished: " + snapshot.getCount()
                                + " items, average " + snapshot.getPercent() + "%");
                    }
                })
                .build();

        ProgressGroup group = ProgressGroup.of(groupConfig);
        ProgressBar kernel = group.add("kernel.tar.gz", 200);
        ProgressBar modules = group.add(
                ProgressConfig.builder().width(16).integer().style(ProgressStyle.HASH).build(),
                "modules.tar.gz", 50);
        ProgressBar docs = group.add("docs.tar.gz", 10);

        group.allDone().thenAccept(snapshots ->
                System.out.println("[allDone] received " + snapshots.size() + " final snapshots"));

        Thread t1 = worker(kernel, 20, 12L);
        Thread t2 = worker(modules, 15, 15L);
        Thread t3 = worker(docs, 10, 25L);
        t1.join();
        t2.join();
        t3.join();

        group.allDone().get(2L, TimeUnit.SECONDS);
        group.close();
        groupConfig.getSink().close();
    }

    private static void logging() throws InterruptedException {
        Logger logger = Logger.getLogger("demo.log.progress");
        logger.setLevel(Level.INFO);

        ProgressConfig config = ProgressConfig.builder()
                .width(18)
                .integer()
                .template(ProgressFormat.COMPACT)
                .refreshIntervalMillis(150L)
                .sink(new CompositeSink(
                        new LoggingSink(new JdkProgressLogger("demo.log.progress", Level.INFO)),
                        ConsoleSink.plain(System.out)))
                .build();

        ProgressBar bar = ProgressBar.builder().name("remote.iso").total(64).config(config).build();
        for (int i = 0; i <= 64; i += 8) {
            bar.update(i);
            sleep(30L);
        }
        bar.close();
        config.getSink().close();
    }

    private static void apiOnly() throws Exception {
        ProgressConfig config = ProgressConfig.builder()
                .sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS)      // no scheduler at all
                .build();

        ProgressBar bar = ProgressBar.builder().name("in-memory").total(200).config(config).build();
        bar.update(75.5);

        System.out.println("value   = " + bar.getValue());
        System.out.println("percent = " + bar.getPercent());
        System.out.println("status  = " + bar.getStatus());
        System.out.println("snapshot= " + bar.snapshot());
        System.out.println("rendered= " + bar.render());

        bar.complete();
        ProgressSnapshot finalSnapshot = bar.future().get(1L, TimeUnit.SECONDS);
        System.out.println("future  = " + finalSnapshot);
        bar.close();
    }

    private static void manualAndAsync() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);

        ProgressConfig config = ProgressConfig.builder()
                .width(16)
                .twoDecimals()
                .style(ProgressStyle.BLOCK)
                .unit("MB")
                .template("[{index}/{count}] {name} {bar} {percent}% "
                        + "({value}/{total}{unit}) elapsed={elapsed} eta={eta}")
                .refreshInterval(0, TimeUnit.MILLISECONDS)      // manual: flush() draws
                .sink(ConsoleSink.plain(System.out))
                .listener(new ProgressListener() {
                    @Override
                    public void onComplete(ProgressSnapshot snapshot) {
                        System.out.println("[async callback] " + snapshot.getName()
                                + " -> " + snapshot.getStatus()
                                + " on thread " + Thread.currentThread().getName());
                        latch.countDown();
                    }
                })
                .build();

        ProgressBar bar = ProgressBar.builder().name("manual.dat").total(10).config(config).build();
        bar.update(2.5);
        bar.flush();
        bar.update(7.0);
        bar.flush();
        bar.complete();

        if (!latch.await(2L, TimeUnit.SECONDS)) {
            System.out.println("[warning] async callback did not arrive in time");
        }
        bar.close();
        config.getSink().close();
    }

    private static void streams() throws Exception {
        byte[] payload = new byte[32 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        ProgressConfig config = ProgressConfig.builder()
                .width(18)
                .integer()
                .style(ProgressStyle.HASH)
                .unit("B")
                .refreshIntervalMillis(60L)
                .sink(ConsoleSink.plain(System.out))
                .template("[{index}/{count}] {name} {bar} {percent}% {value}/{total}{unit} "
                        + "speed={speed}{unit}/s eta={eta}")
                .build();

        ProgressBar download = ProgressBar.builder()
                .name("download.bin").total(payload.length).config(config).build();
        InputStream in = ProgressStreams.wrap(new ByteArrayInputStream(payload), download);
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            received.write(buffer, 0, n);
            Thread.sleep(10L);
        }
        in.close();
        System.out.println("downloaded = " + ByteFormat.format(received.size()));

        ProgressBar upload = ProgressBar.builder()
                .name("upload.bin").total(payload.length).config(config).build();
        OutputStream out = ProgressStreams.wrap(new ByteArrayOutputStream(), upload);
        for (int i = 0; i < payload.length; i += 4096) {
            out.write(payload, i, Math.min(4096, payload.length - i));
            Thread.sleep(5L);
        }
        out.close();
    }

    private static void executionAndIndeterminate() throws Exception {
        ProgressConfig config = ProgressConfig.builder()
                .width(18)
                .integer()
                .refreshIntervalMillis(80L)
                .sink(ConsoleSink.plain(System.out))
                .build();

        ProgressBar steps = ProgressBar.builder().name("migration").total(5).config(config).build();
        ProgressRunner.run(steps, 5, new ProgressRunner.Step() {
            @Override
            public void execute(int index) throws Exception {
                Thread.sleep(30L);
            }
        });

        ProgressBar chunked = ProgressBar.builder()
                .name("chunked.bin")
                .unknownTotal()
                .config(config)
                .build();
        for (int i = 0; i < 8; i++) {
            chunked.increment(2048);
            Thread.sleep(40L);
        }
        chunked.complete();
        System.out.println("final indeterminate snapshot = " + chunked.snapshot());

        // The generic reporter interface lets callers accept any progress implementation.
        ProgressReporter reporter = chunked;
        System.out.println("reporter done = " + reporter.isDone());
    }

    private static Thread worker(final ProgressBar bar, final int steps, final long delayMillis) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                double step = bar.getTotal() / steps;
                for (int i = 0; i < steps; i++) {
                    bar.increment(step);
                    sleep(delayMillis);
                }
                bar.complete();
            }
        }, "producer-" + bar.getName());
        thread.start();
        return thread;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
