package cn.msscn.progress;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.msscn.progress.TestSupport.check;
import static cn.msscn.progress.TestSupport.checkEquals;

/** Multi-thread updates, callback ordering and refresh throttling. */
public final class ConcurrencyTest {

    public static void runAll() throws Exception {
        concurrentUpdates();
        concurrentGroup();
        refreshThrottling();
        callbackOrdering();
        concurrentClose();
    }

    private static ProgressConfig silent() {
        return ProgressConfig.builder().sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS).build();
    }

    private static void concurrentUpdates() throws Exception {
        final int threads = 8;
        final int perThread = 250;
        ProgressBar bar = ProgressBar.builder().name("concurrent")
                .total(threads * perThread).config(silent()).build();
        final CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<Thread>();
        for (int t = 0; t < threads; t++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < perThread; i++) {
                        bar.tick();
                    }
                }
            }, "increment-worker");
            thread.start();
            workers.add(thread);
        }
        start.countDown();
        for (Thread thread : workers) {
            thread.join();
        }
        checkEquals((double) threads * perThread, bar.getValue(), "no lost concurrent increments");
        check(bar.getStatus() == ProgressStatus.COMPLETED, "concurrent bar auto-completes at total");
        check(bar.future().isDone(), "concurrent bar future done");
        bar.close();
    }

    private static void concurrentGroup() throws Exception {
        ProgressGroup group = ProgressGroup.of(silent());
        final int bars = 5;
        final int steps = 100;
        for (int i = 0; i < bars; i++) {
            group.add("item-" + i, steps);
        }
        List<Thread> workers = new ArrayList<Thread>();
        for (int i = 0; i < bars; i++) {
            final ProgressBar bar = group.items().get(i);
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int j = 0; j < steps; j++) {
                        bar.increment(1);
                    }
                }
            }, "group-worker");
            thread.start();
            workers.add(thread);
        }
        for (Thread thread : workers) {
            thread.join();
        }
        List<ProgressSnapshot> snapshots = group.allDone().get(2, TimeUnit.SECONDS);
        checkEquals(bars, snapshots.size(), "group allDone size under concurrency");
        for (ProgressSnapshot s : snapshots) {
            checkEquals((double) steps, s.getValue(), "group bar value under concurrency");
            check(s.getStatus() == ProgressStatus.COMPLETED, "group bar completed");
        }
        checkEquals(bars, group.render().split("\n").length, "group renders all lines");
        group.close();
    }

    private static void refreshThrottling() throws Exception {
        TestSupport.CapturingSink sink = new TestSupport.CapturingSink();
        ProgressConfig config = ProgressConfig.builder()
                .sink(sink)
                .refreshIntervalMillis(50L)
                .build();
        ProgressBar bar = ProgressBar.builder().name("throttled").total(1000).config(config).build();
        for (int i = 1; i <= 200; i++) {
            bar.update(i * 5);
        }
        Thread.sleep(220L);
        int writes = sink.writes.get();
        check(writes >= 1 && writes < 30, "refresh throttled (writes=" + writes + ")");
        check(sink.lastFrame.get().contains("100.00%"), "final frame is rendered");
        bar.close();
        config.getSink().close();
    }

    private static void callbackOrdering() throws Exception {
        final List<String> events = new ArrayList<String>();
        ProgressConfig config = ProgressConfig.builder()
                .sink(NoopSink.INSTANCE)
                .refreshInterval(0, TimeUnit.MILLISECONDS)
                .listener(new ProgressListener() {
                    @Override public void onProgress(ProgressSnapshot snapshot) {
                        events.add("progress");
                    }
                    @Override public void onComplete(ProgressSnapshot snapshot) {
                        events.add("complete");
                    }
                })
                .build();
        ProgressBar bar = ProgressBar.builder().name("order").total(2).config(config).build();
        bar.update(1);
        bar.complete();
        bar.future().get(1, TimeUnit.SECONDS);
        Thread.sleep(100L);
        check(events.size() >= 2, "callbacks delivered");
        check(events.get(events.size() - 1).equals("complete"), "complete is the last callback");
        bar.close();
    }

    private static void concurrentClose() throws Exception {
        final ProgressBar bar = ProgressBar.builder().name("closing")
                .total(10000).config(silent()).build();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < 10000; i++) {
                        bar.tick();
                    }
                } catch (Throwable t) {
                    error.set(t);
                }
            }
        }, "close-producer");
        producer.start();
        bar.close();
        producer.join();
        check(error.get() == null, "close during updates does not throw");
        bar.close();
        check(bar.isDone(), "bar still completes after early close");
    }
}
