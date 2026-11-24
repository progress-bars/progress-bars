package cn.msscn.progress;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Shared refresh/dispatch engine for a single bar or a whole group.
 * Refreshes are throttled: updates only mark a frame dirty, the scheduler
 * writes at most once per configured interval.
 */
final class ProgressEngine implements AutoCloseable {

    private final ProgressSink sink;
    private final long intervalMillis;
    private final Supplier<String> frameSupplier;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Object lock = new Object();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private boolean started;
    private boolean closed;
    private volatile boolean continuous;

    ProgressEngine(ProgressConfig config, Supplier<String> frameSupplier) {
        this.sink = config.getSink();
        this.intervalMillis = config.getRefreshIntervalMillis();
        this.frameSupplier = frameSupplier;
    }

    void start() {
        synchronized (lock) {
            if (started || closed) {
                return;
            }
            started = true;
        }
        if (intervalMillis <= 0) {
            renderNow();
            return;
        }
        synchronized (lock) {
            scheduler = Executors.newSingleThreadScheduledExecutor(
                    ProgressExecutors.threadFactory("progress-refresh"));
            task = scheduler.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    if (continuous || dirty.get()) {
                        try {
                            renderNow();
                        } catch (RuntimeException ignored) {
                            // A broken sink must not kill the refresh loop.
                        }
                    }
                }
            }, 0L, intervalMillis, TimeUnit.MILLISECONDS);
        }
    }

    /** Mark the current frame outdated; the next tick will draw it. */
    void requestRender() {
        dirty.set(true);
    }

    /** Keep redrawing every tick (used by indeterminate spinners). */
    void setContinuous(boolean value) {
        continuous = value;
    }

    /** Draw immediately (used for the first and the final frame, and in manual mode). */
    void renderNow() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            dirty.set(false);
            String frame = frameSupplier.get();
            sink.write(frame);
        }
    }

    /** Stop periodic rendering after the final frame, without closing the sink. */
    void stopScheduler() {
        synchronized (lock) {
            if (task != null) {
                task.cancel(false);
                task = null;
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
        }
    }

    /** Also used by bars living inside a group, which have no render engine of their own. */
    static void dispatch(ProgressConfig config, Runnable callback) {
        if (config == null || config.getListener() == null || config.getListenerExecutor() == null) {
            return;
        }
        try {
            config.getListenerExecutor().execute(callback);
        } catch (RuntimeException ignored) {
            // A shut-down custom executor must not break progress reporting.
        }
    }

    boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    ProgressSink getSink() {
        return sink;
    }

    /** Stops the scheduler. The sink is owned by the caller and stays open. */
    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            if (task != null) {
                task.cancel(false);
            }
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
        }
        sink.flush();
    }
}
