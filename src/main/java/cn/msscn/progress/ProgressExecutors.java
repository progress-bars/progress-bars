package cn.msscn.progress;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared daemon threads; they never keep a JVM alive after main() returns. */
final class ProgressExecutors {

    /** Callbacks are serialized to keep their order stable. */
    static final Executor CALLBACKS = Executors.newSingleThreadExecutor(threadFactory("progress-callback"));

    private ProgressExecutors() {
    }

    static ThreadFactory threadFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, name + "-" + seq.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };
    }
}
