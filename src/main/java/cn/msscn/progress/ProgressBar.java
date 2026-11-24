package cn.msscn.progress;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A single progress task.
 *
 * <p>Three ways to use it:</p>
 * <ol>
 *   <li>Console/log rendering: just call {@link #update(double)}; refresh is throttled.</li>
 *   <li>Pure API: configure {@link NoopSink} and poll {@link #getValue()}/{@link #getPercent()}.</li>
 *   <li>Async: attach a {@link ProgressListener} or wait on {@link #future()}.</li>
 * </ol>
 *
 * <p>Mutation methods are synchronized, so several producer threads may update
 * the same bar safely. Snapshots handed to callbacks are immutable.</p>
 */
public final class ProgressBar implements ProgressReporter, AutoCloseable {

    private final ProgressConfig config;
    private final ProgressGroup group;
    private final int index;
    private final String name;
    private final ProgressEngine engine;
    private final CompletableFuture<ProgressSnapshot> future = new CompletableFuture<ProgressSnapshot>();
    private final AtomicBoolean finished = new AtomicBoolean(false);

    private volatile double total;
    private volatile double value;
    private volatile double speed;
    private volatile ProgressStatus status = ProgressStatus.PENDING;
    private volatile long startedAtMillis;
    private volatile long updatedAtMillis;
    private volatile long lastSpeedNanos;
    private volatile boolean indeterminate;

    private ProgressBar(Builder builder, ProgressGroup group, int index) {
        if (builder.name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (!Double.isFinite(builder.total) || (builder.total <= 0 && !builder.indeterminate)) {
            throw new IllegalArgumentException("total must be a positive finite number");
        }
        this.name = builder.name;
        this.total = builder.total;
        this.config = builder.config == null ? ProgressConfig.defaults() : builder.config;
        this.group = group;
        this.index = index;
        this.indeterminate = builder.indeterminate;
        this.engine = group == null
                ? new ProgressEngine(this.config, new java.util.function.Supplier<String>() {
                    @Override
                    public String get() {
                        return render();
                    }
                })
                : null;
    }

    /**
     * Creates a builder for a standalone bar.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Shortcut for {@code builder().name(name).total(total).build()}. */
    public static ProgressBar of(String name, double total) {
        return builder().name(name).total(total).build();
    }

    /** Total defaults to 100. */
    public static ProgressBar of(String name) {
        return of(name, 100.0);
    }

    /**
     * Creates a standalone bar with an explicit total and configuration.
     *
     * @param name the task name shown in the frame
     * @param total the target value; must be a positive finite number
     * @param config rendering configuration, or null for {@link ProgressConfig#defaults()}
     * @return the new bar
     */
    public static ProgressBar of(String name, double total, ProgressConfig config) {
        return builder().name(name).total(total).config(config).build();
    }

    /**
     * Creates a standalone bar defaulting to a total of 100.
     *
     * @param name the task name shown in the frame
     * @param config rendering configuration, or null for defaults
     * @return the new bar
     */
    public static ProgressBar of(String name, ProgressConfig config) {
        return of(name, 100.0, config);
    }

    /** Unknown total: a spinner is shown instead of a percentage. */
    public static ProgressBar unknown(String name) {
        return builder().name(name).unknownTotal().build();
    }

    /**
     * Creates an indeterminate bar with an explicit configuration.
     *
     * @param name the task name shown in the frame
     * @param config rendering configuration, or null for defaults
     * @return the new bar
     */
    public static ProgressBar unknown(String name, ProgressConfig config) {
        return builder().name(name).unknownTotal().config(config).build();
    }

    static ProgressBar forGroup(ProgressGroup group, int index, ProgressConfig config,
                                String name, double total, boolean indeterminate) {
        Builder b = new Builder().name(name).total(total).config(config);
        if (indeterminate) {
            b.unknownTotal();
        }
        return new ProgressBar(b, group, index);
    }

    /** @return the task name shown in the frame */
    public String getName() {
        return name;
    }

    /** @return the configuration backing this bar */
    public ProgressConfig getConfig() {
        return config;
    }

    /** @return the 1-based position in a group, or 1 for a standalone bar */
    public int getIndex() {
        return index;
    }

    /** @return the current progress value */
    public double getValue() {
        return value;
    }

    /** @return the target value */
    public double getTotal() {
        return total;
    }

    /** True when the total is unknown (spinner instead of percentage). */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    /** Percentage clamped to [0, 100]; 0 while indeterminate. */
    public double getPercent() {
        if (indeterminate || total <= 0) {
            return 0.0;
        }
        double p = value / total * 100.0;
        return Math.max(0.0, Math.min(100.0, p));
    }

    /** Units per second; 0 until at least two updates have been observed. */
    public double getSpeed() {
        return speed;
    }

    /** @return the current lifecycle status */
    public ProgressStatus getStatus() {
        return status;
    }

    /** @return true when the bar is completed or cancelled */
    public boolean isDone() {
        return status == ProgressStatus.COMPLETED || status == ProgressStatus.CANCELLED;
    }

    /** Immutable view of the current state; use this for pure API access. */
    public ProgressSnapshot snapshot() {
        int count = group == null ? 1 : group.size();
        return new ProgressSnapshot(index, count, name, value, total, getPercent(), speed,
                status, startedAtMillis, updatedAtMillis, indeterminate);
    }

    /** Completed with the final snapshot on complete()/cancel(). */
    public CompletableFuture<ProgressSnapshot> future() {
        return future;
    }

    /** Moves to RUNNING and draws the first frame. Calling it twice is harmless. */
    public synchronized ProgressBar start() {
        if (status == ProgressStatus.PENDING) {
            status = ProgressStatus.RUNNING;
            long now = System.currentTimeMillis();
            startedAtMillis = now;
            updatedAtMillis = now;
            if (engine != null) {
                engine.setContinuous(indeterminate);
                engine.start();
            } else {
                group.start();
                group.onBarChanged();
            }
        }
        return this;
    }

    /** Absolute progress value. Reaching {@code total} completes the bar automatically. */
    public synchronized ProgressBar update(double newValue) {
        start();
        if (finished.get()) {
            return this;
        }
        double old = value;
        double next = Double.isNaN(newValue) ? old : newValue;
        if (!indeterminate) {
            next = Math.max(0.0, Math.min(total, next));
        } else {
            next = Math.max(0.0, next);
        }
        long nowNanos = System.nanoTime();
        value = next;
        updatedAtMillis = System.currentTimeMillis();
        updateSpeed(old, next, nowNanos);
        notifyChanged();
        if (!indeterminate && next >= total) {
            complete();
        }
        return this;
    }

    /** Relative progress value. */
    public synchronized ProgressBar increment(double delta) {
        return update(value + (Double.isNaN(delta) ? 0.0 : delta));
    }

    /** Advance by one unit; handy in loops and step counters. */
    public synchronized ProgressBar tick() {
        return increment(1.0);
    }

    /** Change the target while running; the current value is clamped to the new total. */
    public synchronized ProgressBar setTotal(double newTotal) {
        if (finished.get()) {
            return this;
        }
        if (!Double.isFinite(newTotal) || newTotal <= 0) {
            throw new IllegalArgumentException("total must be a positive finite number");
        }
        indeterminate = false;
        if (engine != null) {
            engine.setContinuous(false);
        }
        total = newTotal;
        if (value > newTotal) {
            value = newTotal;
        }
        updatedAtMillis = System.currentTimeMillis();
        notifyChanged();
        return this;
    }

    /** Mark as 100% done, fire callbacks and complete the future. Idempotent. */
    public synchronized ProgressBar complete() {
        if (!finished.compareAndSet(false, true)) {
            return this;
        }
        if (indeterminate) {
            total = Math.max(value, 1.0);
            indeterminate = false;
        }
        value = total;
        if (startedAtMillis == 0L) {
            startedAtMillis = System.currentTimeMillis();
        }
        updatedAtMillis = System.currentTimeMillis();
        status = ProgressStatus.COMPLETED;
        ProgressSnapshot snapshot = snapshot();
        drawFinalFrame();
        future.complete(snapshot);
        fire(EngineEvent.COMPLETE, snapshot);
        return this;
    }

    /** Stop at the current value and complete the future with a CANCELLED snapshot. */
    public synchronized ProgressBar cancel() {
        if (!finished.compareAndSet(false, true)) {
            return this;
        }
        status = ProgressStatus.CANCELLED;
        updatedAtMillis = System.currentTimeMillis();
        ProgressSnapshot snapshot = snapshot();
        drawFinalFrame();
        future.complete(snapshot);
        fire(EngineEvent.CANCEL, snapshot);
        return this;
    }

    /** Rendered single line, without printing it. Uses the configured formatter. */
    public String render() {
        return config.getFormatter().format(snapshot(), config);
    }

    /** One-off render with another formatter; the bar's configuration is unchanged. */
    public String render(ProgressFormatter formatter) {
        if (formatter == null) {
            throw new IllegalArgumentException("formatter must not be null");
        }
        return formatter.format(snapshot(), config);
    }

    /** Force a redraw now (mainly for {@code refreshInterval(0)} manual mode). */
    public void flush() {
        if (engine != null) {
            engine.renderNow();
        } else {
            group.flush();
        }
    }

    /** Stops the refresh scheduler. The configured sink stays open; close it yourself. */
    @Override
    public void close() {
        if (engine != null) {
            engine.close();
        }
        // A grouped bar is owned by the ProgressGroup.
    }

    private enum EngineEvent {
        PROGRESS, COMPLETE, CANCEL
    }

    private void fire(EngineEvent event, final ProgressSnapshot snapshot) {
        Runnable callback;
        switch (event) {
            case COMPLETE:
                callback = new Runnable() {
                    @Override
                    public void run() {
                        config.getListener().onComplete(snapshot);
                    }
                };
                break;
            case CANCEL:
                callback = new Runnable() {
                    @Override
                    public void run() {
                        config.getListener().onCancel(snapshot);
                    }
                };
                break;
            default:
                callback = new Runnable() {
                    @Override
                    public void run() {
                        config.getListener().onProgress(snapshot);
                    }
                };
        }
        // Same helper for standalone and grouped bars: a custom executor must
        // never be able to break a progress update.
        ProgressEngine.dispatch(config, callback);
    }

    private void updateSpeed(double oldValue, double newValue, long nowNanos) {
        if (lastSpeedNanos > 0L && nowNanos > lastSpeedNanos) {
            double seconds = (nowNanos - lastSpeedNanos) / 1000000000.0;
            double instant = (newValue - oldValue) / seconds;
            if (instant >= 0.0) {
                speed = speed <= 0.0 ? instant : speed * 0.7 + instant * 0.3;
            }
        }
        lastSpeedNanos = nowNanos;
    }

    private void notifyChanged() {
        if (engine != null) {
            engine.requestRender();
            fire(EngineEvent.PROGRESS, snapshot());
        } else {
            group.onBarChanged();
            fire(EngineEvent.PROGRESS, snapshot());
        }
    }

    private void drawFinalFrame() {
        if (engine != null) {
            engine.setContinuous(false);
            engine.requestRender();
            engine.renderNow();
            engine.stopScheduler();
        } else {
            group.onBarFinalFrame();
        }
    }

    /** Builder for {@link ProgressBar} instances. */
    public static final class Builder {

        private String name;
        private double total = 100.0;
        private ProgressConfig config;
        private boolean indeterminate;

        /** Unknown total: renders a spinner and counts bytes/steps without a percentage. */
        public Builder unknownTotal() {
            this.indeterminate = true;
            return this;
        }

        /**
         * Sets the task name.
         *
         * @param name the name shown in the frame; must not be null
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the target value.
         *
         * @param total a positive finite number
         * @return this builder
         */
        public Builder total(double total) {
            this.total = total;
            return this;
        }

        /**
         * Sets the rendering configuration.
         *
         * @param config the configuration, or null for defaults
         * @return this builder
         */
        public Builder config(ProgressConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Builds a standalone bar.
         *
         * @return the new bar
         */
        public ProgressBar build() {
            return new ProgressBar(this, null, 1);
        }
    }
}
