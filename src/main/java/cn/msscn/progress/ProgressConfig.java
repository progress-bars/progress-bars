package cn.msscn.progress;

import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Immutable configuration shared by a bar or inherited by a group's children. */
public final class ProgressConfig {

    /** Renders one line exactly as the built-in template engine does. */
    public static final ProgressFormatter DEFAULT_FORMATTER = new ProgressFormatter() {
        @Override
        public String format(ProgressSnapshot snapshot, ProgressConfig config) {
            return ProgressFormat.format(snapshot, config);
        }
    };

    /** Default group renderer: one line per snapshot, in order. */
    public static final ProgressGroupFormatter DEFAULT_GROUP_FORMATTER = new ProgressGroupFormatter() {
        @Override
        public String format(java.util.List<ProgressSnapshot> snapshots, ProgressConfig config) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < snapshots.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                }
                out.append(config.getFormatter().format(snapshots.get(i), config));
            }
            return out.toString();
        }
    };

    private final int width;
    private final int decimals;
    private final long refreshIntervalMillis;
    private final ProgressStyle style;
    private final String template;
    private final String unit;
    private final ProgressSink sink;
    private final ProgressListener listener;
    private final Executor listenerExecutor;
    private final ProgressFormatter formatter;
    private final ProgressGroupFormatter groupFormatter;
    private final Map<String, String> extras;

    private ProgressConfig(Builder b) {
        this.width = b.width;
        this.decimals = b.decimals;
        this.refreshIntervalMillis = b.refreshIntervalMillis;
        this.style = b.style;
        this.template = b.template;
        this.unit = b.unit;
        this.sink = b.sink;
        this.listener = b.listener;
        this.listenerExecutor = b.listenerExecutor;
        this.formatter = b.formatter;
        this.groupFormatter = b.groupFormatter;
        this.extras = Collections.unmodifiableMap(new HashMap<String, String>(b.extras));
    }

    /**
     * Returns a configuration with all built-in defaults.
     *
     * @return a fresh default configuration
     */
    public static ProgressConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a builder with all built-in defaults.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** @return the bar width in characters, excluding brackets, name and percent */
    public int getWidth() {
        return width;
    }

    /** 0 = integer percent, 2 = two decimals, up to 6 accepted. */
    public int getDecimals() {
        return decimals;
    }

    /** 0 disables the scheduler: call flush()/render() yourself. */
    public long getRefreshIntervalMillis() {
        return refreshIntervalMillis;
    }

    /** @return the character set used to draw the bar */
    public ProgressStyle getStyle() {
        return style;
    }

    /** @return the render template used by the built-in formatter */
    public String getTemplate() {
        return template;
    }

    /** @return the unit appended to numeric values, possibly empty */
    public String getUnit() {
        return unit;
    }

    /** @return the output sink; never null once built */
    public ProgressSink getSink() {
        return sink;
    }

    /** @return the callback listener; never null once built */
    public ProgressListener getListener() {
        return listener;
    }

    /** @return the executor used to dispatch callbacks */
    public Executor getListenerExecutor() {
        return listenerExecutor;
    }

    /** Custom per-bar renderer; never null. */
    public ProgressFormatter getFormatter() {
        return formatter;
    }

    /** Custom whole-group renderer; never null. */
    public ProgressGroupFormatter getGroupFormatter() {
        return groupFormatter;
    }

    /** True when a caller supplied a renderer for the whole group. */
    public boolean hasCustomGroupFormatter() {
        return groupFormatter != DEFAULT_GROUP_FORMATTER;
    }

    /** Immutable custom placeholder values used by the template engine. */
    public Map<String, String> getExtras() {
        return extras;
    }

    /**
     * Creates a builder initialised with this configuration's values.
     *
     * @return a new builder
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Builder for {@link ProgressConfig} instances. */
    public static final class Builder {

        private int width = 30;
        private int decimals = 2;
        private long refreshIntervalMillis = 100L;
        private ProgressStyle style = ProgressStyle.ASCII;
        private String template = ProgressFormat.DEFAULT;
        private String unit = "";
        private ProgressSink sink;
        private ProgressListener listener;
        private Executor listenerExecutor;
        private ProgressFormatter formatter = DEFAULT_FORMATTER;
        private ProgressGroupFormatter groupFormatter = DEFAULT_GROUP_FORMATTER;
        private Map<String, String> extras = Collections.emptyMap();

        /** Creates a builder with all built-in defaults. */
        public Builder() {
        }

        private Builder(ProgressConfig c) {
            this.width = c.width;
            this.decimals = c.decimals;
            this.refreshIntervalMillis = c.refreshIntervalMillis;
            this.style = c.style;
            this.template = c.template;
            this.unit = c.unit;
            this.sink = c.sink;
            this.listener = c.listener;
            this.listenerExecutor = c.listenerExecutor;
            this.formatter = c.formatter;
            this.groupFormatter = c.groupFormatter;
            this.extras = c.extras;
        }

        /** Bar width in characters, excluding brackets/name/percent. */
        public Builder width(int width) {
            if (width < 1) {
                throw new IllegalArgumentException("width must be >= 1");
            }
            this.width = width;
            return this;
        }

        /** 0 = 67%, 2 = 66.67%. Values outside 0..6 are rejected. */
        public Builder decimals(int decimals) {
            if (decimals < 0 || decimals > 6) {
                throw new IllegalArgumentException("decimals must be between 0 and 6");
            }
            this.decimals = decimals;
            return this;
        }

        /**
         * Sets percent precision to zero decimals, e.g. {@code 67%}.
         *
         * @return this builder
         */
        public Builder integer() {
            return decimals(0);
        }

        /**
         * Sets percent precision to two decimals, e.g. {@code 66.67%}.
         *
         * @return this builder
         */
        public Builder twoDecimals() {
            return decimals(2);
        }

        /**
         * Sets the refresh interval.
         *
         * @param millis interval in milliseconds; 0 disables the scheduler
         * @return this builder
         * @throws IllegalArgumentException if {@code millis} is negative
         */
        public Builder refreshIntervalMillis(long millis) {
            if (millis < 0) {
                throw new IllegalArgumentException("refresh interval must be >= 0");
            }
            this.refreshIntervalMillis = millis;
            return this;
        }

        /** Convenience for seconds/milliseconds/etc. Zero means manual refresh only. */
        public Builder refreshInterval(long value, TimeUnit unit) {
            if (unit == null) {
                throw new IllegalArgumentException("time unit must not be null");
            }
            return refreshIntervalMillis(unit.toMillis(value));
        }

        /** Milliseconds shorthand. */
        public Builder refreshInterval(long millis) {
            return refreshIntervalMillis(millis);
        }

        /**
         * Sets the bar character set.
         *
         * @param style the style to use
         * @return this builder
         * @throws IllegalArgumentException if {@code style} is null
         */
        public Builder style(ProgressStyle style) {
            if (style == null) {
                throw new IllegalArgumentException("style must not be null");
            }
            this.style = style;
            return this;
        }

        /**
         * Sets the render template.
         *
         * @param template a template containing {@code {placeholder}} tokens
         * @return this builder
         * @throws IllegalArgumentException if {@code template} is null
         */
        public Builder template(String template) {
            if (template == null) {
                throw new IllegalArgumentException("template must not be null");
            }
            this.template = template;
            return this;
        }

        /** Unit shown by the {unit} placeholder, e.g. "B" or "MB". */
        public Builder unit(String unit) {
            this.unit = unit == null ? "" : unit;
            return this;
        }

        /**
         * Sets the output sink.
         *
         * @param sink the destination for rendered frames
         * @return this builder
         * @throws IllegalArgumentException if {@code sink} is null
         */
        public Builder sink(ProgressSink sink) {
            if (sink == null) {
                throw new IllegalArgumentException("sink must not be null");
            }
            this.sink = sink;
            return this;
        }

        /** Shorthand for {@code sink(ConsoleSink.plain(stream))}. */
        public Builder sink(PrintStream stream) {
            if (stream == null) {
                throw new IllegalArgumentException("stream must not be null");
            }
            return sink(ConsoleSink.plain(stream));
        }

        /** Shorthand for {@code sink(new LoggingSink(logger))}. */
        public Builder logger(ProgressLogger logger) {
            return sink(new LoggingSink(logger));
        }

        /** Route each whole frame to your own code, e.g. {@code sink(System.out::print)}. */
        public Builder sink(Consumer<String> frameConsumer) {
            return sink(ConsumerSink.of(frameConsumer));
        }

        /** Route each non-empty frame line to your own code, e.g. {@code sinkLines(log::info)}. */
        public Builder sinkLines(Consumer<String> lineConsumer) {
            return sink(ConsumerSink.lines(lineConsumer));
        }

        /** Write frames to a stream, optionally appending a trailing newline. */
        public Builder printStream(PrintStream stream, boolean newline) {
            if (stream == null) {
                throw new IllegalArgumentException("stream must not be null");
            }
            return sink(new PrintStreamSink(stream, newline));
        }

        /** Log each frame line through a lambda, e.g. {@code logWith(msg -> log.info("{}", msg))}. */
        public Builder logWith(Consumer<String> lineConsumer) {
            return sink(LoggingSink.of(lineConsumer));
        }

        /** Replace the built-in line renderer with your own formatter. */
        public Builder formatter(ProgressFormatter formatter) {
            if (formatter == null) {
                throw new IllegalArgumentException("formatter must not be null");
            }
            this.formatter = formatter;
            return this;
        }

        /** Render the whole group yourself (add headers, footers, summaries, ...). */
        public Builder groupFormatter(ProgressGroupFormatter groupFormatter) {
            if (groupFormatter == null) {
                throw new IllegalArgumentException("group formatter must not be null");
            }
            this.groupFormatter = groupFormatter;
            return this;
        }

        /** Add a custom {@code {key}} placeholder value for the template engine. */
        public Builder extra(String key, String value) {
            if (key == null || key.length() == 0) {
                throw new IllegalArgumentException("placeholder key must not be empty");
            }
            if (value == null) {
                throw new IllegalArgumentException("placeholder value must not be null");
            }
            Map<String, String> copy = new HashMap<String, String>(extras);
            copy.put(key, value);
            this.extras = copy;
            return this;
        }

        /** Add several custom placeholders at once. */
        public Builder extras(Map<String, String> values) {
            if (values == null) {
                throw new IllegalArgumentException("extras must not be null");
            }
            Map<String, String> copy = new HashMap<String, String>(extras);
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() == 0
                        || entry.getValue() == null) {
                    throw new IllegalArgumentException("extra keys and values must not be null");
                }
                copy.put(entry.getKey(), entry.getValue());
            }
            this.extras = copy;
            return this;
        }

        /**
         * Sets the progress callback listener.
         *
         * @param listener the listener, or null to disable callbacks
         * @return this builder
         */
        public Builder listener(ProgressListener listener) {
            this.listener = listener;
            return this;
        }

        /** Explicitly disable callbacks (used for children of a ProgressGroup). */
        public Builder noListener() {
            this.listener = NOOP_LISTENER;
            return this;
        }

        /**
         * Sets the executor used to dispatch callbacks.
         *
         * @param executor the executor to use
         * @return this builder
         * @throws IllegalArgumentException if {@code executor} is null
         */
        public Builder listenerExecutor(Executor executor) {
            if (executor == null) {
                throw new IllegalArgumentException("listener executor must not be null");
            }
            this.listenerExecutor = executor;
            return this;
        }

        /** Register a listener and its executor in one call. */
        public Builder callback(ProgressListener listener, Executor executor) {
            listener(listener);
            listenerExecutor(executor);
            return this;
        }

        /**
         * Builds the configuration, filling defaults for unset fields.
         *
         * @return the immutable configuration
         */
        public ProgressConfig build() {
            if (sink == null) {
                sink = new ConsoleSink();
            }
            if (listener == null) {
                listener = NOOP_LISTENER;
            }
            if (listenerExecutor == null) {
                listenerExecutor = ProgressExecutors.CALLBACKS;
            }
            return new ProgressConfig(this);
        }

        private static final ProgressListener NOOP_LISTENER = new ProgressListener() {
        };
    }
}
