package cn.msscn.progress;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Several progress bars rendered together, one per line.
 *
 * <p>Every line carries its own {@code [index/count]} prefix, e.g.</p>
 * <pre>
 * [1/3] kernel.tar.gz    [==========>        ] 55.00%
 * [2/3] modules.tar.gz   [=====>             ] 25.00%
 * [3/3] docs.tar.gz      [================== ] 98.00%
 * </pre>
 *
 * <p>Bars normally advance from different threads; all mutation is
 * synchronized and only one refresh thread writes to the sink.</p>
 */
public final class ProgressGroup implements AutoCloseable {

    private final ProgressConfig config;
    private final ProgressEngine engine;
    private final List<ProgressBar> bars = new ArrayList<ProgressBar>();
    private final CompletableFuture<List<ProgressSnapshot>> future =
            new CompletableFuture<List<ProgressSnapshot>>();
    private final AtomicBoolean allDone = new AtomicBoolean(false);

    private ProgressGroup(ProgressConfig config) {
        this.config = config;
        this.engine = new ProgressEngine(config, new java.util.function.Supplier<String>() {
            @Override
            public String get() {
                return render();
            }
        });
    }

    /**
     * Creates a builder for a new group.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty group with an explicit configuration.
     *
     * @param config the shared configuration
     * @return the new group
     */
    public static ProgressGroup of(ProgressConfig config) {
        return builder().config(config).build();
    }

    /** Add a bar inheriting the group's configuration. */
    public synchronized ProgressBar add(String name, double total) {
        return add(null, name, total);
    }

    /** Add a bar with the default total of 100. */
    public synchronized ProgressBar add(String name) {
        return add(name, 100.0);
    }

    /** Add a bar whose total is the file length (0 becomes unknown total). */
    public synchronized ProgressBar addFile(File file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (file.length() <= 0) {
            return addUnknown(file.getName());
        }
        return add(overrideForFile(file), file.getName(), file.length());
    }

    /**
     * Adds a bar whose total is the length of the given file.
     *
     * @param path the file to track
     * @return the new bar
     * @throws IllegalArgumentException if {@code path} is null
     */
    public synchronized ProgressBar addFile(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return addFile(path.toFile());
    }

    private ProgressConfig overrideForFile(File file) {
        return config.toBuilder()
                .unit(config.getUnit() == null || config.getUnit().isEmpty() ? "B" : config.getUnit())
                .noListener()
                .build();
    }

    /** Add a bar with its own template/style/precision; its sink is ignored (the group owns it). */
    public synchronized ProgressBar add(ProgressConfig override, String name, double total) {
        // The group's listener reports the aggregate once; children must not
        // fire the same listener again. An explicit override may set its own.
        ProgressConfig childConfig = override == null
                ? config.toBuilder().noListener().build()
                : override;
        ProgressBar bar = ProgressBar.forGroup(this, bars.size() + 1,
                childConfig, name, total, false);
        bars.add(bar);
        engine.requestRender();
        return bar;
    }

    /** Add a bar whose total is unknown (spinner, e.g. chunked downloads). */
    public synchronized ProgressBar addUnknown(String name) {
        return addUnknown(name, null);
    }

    /**
     * Adds an indeterminate bar with an optional per-bar configuration override.
     *
     * @param name the task name shown in the frame
     * @param override per-bar configuration, or null to inherit the group's
     * @return the new bar
     */
    public synchronized ProgressBar addUnknown(String name, ProgressConfig override) {
        ProgressConfig childConfig = override == null
                ? config.toBuilder().noListener().build()
                : override;
        ProgressBar bar = ProgressBar.forGroup(this, bars.size() + 1,
                childConfig, name, 0.0, true);
        bars.add(bar);
        engine.requestRender();
        return bar;
    }

    /** @return the number of bars in this group */
    public synchronized int size() {
        return bars.size();
    }

    /** @return an unmodifiable view of the bars, in display order */
    public synchronized List<ProgressBar> items() {
        return Collections.unmodifiableList(new ArrayList<ProgressBar>(bars));
    }

    /** @return an unmodifiable list of the current snapshots, in display order */
    public synchronized List<ProgressSnapshot> snapshots() {
        List<ProgressSnapshot> result = new ArrayList<ProgressSnapshot>(bars.size());
        for (ProgressBar bar : bars) {
            result.add(bar.snapshot());
        }
        return Collections.unmodifiableList(result);
    }

    /** All lines, '\n' separated. Does not print anything. */
    public synchronized String render() {
        if (config.hasCustomGroupFormatter()) {
            return config.getGroupFormatter().format(snapshots(), config);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(bars.get(i).render());
        }
        return sb.toString();
    }

    /** One-off whole-group render with another formatter; configuration is unchanged. */
    public synchronized String render(ProgressGroupFormatter formatter) {
        if (formatter == null) {
            throw new IllegalArgumentException("formatter must not be null");
        }
        return formatter.format(snapshots(), config);
    }

    /** Starts the refresh scheduler; also called implicitly when the first bar starts. */
    public synchronized void start() {
        engine.start();
    }

    /** Force a redraw now (mainly for manual refresh mode). */
    public void flush() {
        engine.renderNow();
    }

    /** Completed with all final snapshots once every bar is done. */
    public CompletableFuture<List<ProgressSnapshot>> allDone() {
        return future;
    }

    void onBarChanged() {
        engine.setContinuous(hasIndeterminate());
        engine.requestRender();
    }

    void onBarFinalFrame() {
        engine.setContinuous(hasIndeterminate());
        engine.requestRender();
        engine.renderNow();
        checkAllDone();
    }

    private synchronized boolean hasIndeterminate() {
        for (ProgressBar bar : bars) {
            if (bar.isIndeterminate() && !bar.isDone()) {
                return true;
            }
        }
        return false;
    }

    private void checkAllDone() {
        if (allDone.get()) {
            return;
        }
        synchronized (this) {
            if (allDone.get() || bars.isEmpty()) {
                return;
            }
            for (ProgressBar bar : bars) {
                if (!bar.isDone()) {
                    return;
                }
            }
            allDone.set(true);
            final ProgressSnapshot summary = aggregateSnapshot();
            future.complete(snapshots());
            ProgressEngine.dispatch(config, new Runnable() {
                @Override
                public void run() {
                    config.getListener().onComplete(summary);
                }
            });
            engine.close();
        }
    }

    private ProgressSnapshot aggregateSnapshot() {
        double total = 0.0;
        double value = 0.0;
        double percent = 0.0;
        boolean cancelled = false;
        long minStart = Long.MAX_VALUE;
        long maxUpdate = 0L;
        synchronized (this) {
            for (ProgressBar bar : bars) {
                ProgressSnapshot s = bar.snapshot();
                total += s.getTotal();
                value += s.getValue();
                percent += s.getPercent();
                cancelled |= s.getStatus() == ProgressStatus.CANCELLED;
                if (s.getStartedAtMillis() > 0) {
                    minStart = Math.min(minStart, s.getStartedAtMillis());
                }
                maxUpdate = Math.max(maxUpdate, s.getUpdatedAtMillis());
            }
            if (minStart == Long.MAX_VALUE) {
                minStart = 0L;
            }
            return new ProgressSnapshot(1, bars.size(), "group", value, total,
                    bars.isEmpty() ? 0.0 : percent / bars.size(), 0.0,
                    cancelled ? ProgressStatus.CANCELLED : ProgressStatus.COMPLETED,
                    minStart, maxUpdate);
        }
    }

    /** Stops the refresh scheduler. The configured sink stays open; close it yourself. */
    @Override
    public void close() {
        engine.close();
    }

    /** Builder for {@link ProgressGroup} instances. */
    public static final class Builder {

        private ProgressConfig config;

        /**
         * Sets the configuration inherited by all bars in the group.
         *
         * @param config the shared configuration, or null for defaults
         * @return this builder
         */
        public Builder config(ProgressConfig config) {
            this.config = config;
            return this;
        }

        /**
         * Builds an empty group.
         *
         * @return the new group
         */
        public ProgressGroup build() {
            ProgressConfig c = config == null ? ProgressConfig.defaults() : config;
            return new ProgressGroup(c);
        }
    }
}
