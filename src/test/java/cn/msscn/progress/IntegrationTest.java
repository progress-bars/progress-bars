package cn.msscn.progress;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Host-application integration: own output channel, own logging, own formatting. */
public final class IntegrationTest {

    private IntegrationTest() {
    }

    public static void runAll() throws Exception {
        consumerFrames();
        consumerLines();
        loggingAdapters();
        printStreamModes();
        customFormatters();
        extraPlaceholders();
        groupFormatters();
        pureApiRendering();
        manualRefreshMode();
        invalidArguments();
        System.out.println("    integration: 11 scenarios");
    }

    private static void consumerFrames() {
        final List<String> frames = new ArrayList<String>();
        ProgressConfig c = ProgressConfig.builder()
                .sink(ConsumerSink.of(new java.util.function.Consumer<String>() {
                    @Override public void accept(String frame) {
                        frames.add(frame);
                    }
                }))
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("frame", 10, c);
        bar.start();
        bar.update(5);
        bar.flush();
        TestSupport.check(frames.size() >= 2, "ConsumerSink receives frames");
        TestSupport.check(frames.get(frames.size() - 1).contains("50.00%"),
                "frame consumer sees current percent");
        bar.complete();
        bar.close();
    }

    private static void consumerLines() {
        final List<String> lines = new ArrayList<String>();
        ProgressConfig c = ProgressConfig.builder()
                .sink(ConsumerSink.lines(new java.util.function.Consumer<String>() {
                    @Override public void accept(String line) {
                        lines.add(line);
                    }
                }))
                .template("line {percent}%")
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("lines", 10, c);
        bar.start();
        bar.complete();
        TestSupport.check(lines.size() >= 1, "ConsumerSink.lines receives lines");
        TestSupport.check(lines.get(0).startsWith("line "), "line consumer gets formatted text");
        for (String line : lines) {
            TestSupport.check(line.length() > 0, "line consumer skips empty lines");
        }
        bar.close();
    }

    private static void loggingAdapters() {
        final List<String> logged = new ArrayList<String>();
        ProgressConfig c = ProgressConfig.builder()
                .logWith(new java.util.function.Consumer<String>() {
                    @Override public void accept(String message) {
                        logged.add("MY-APP " + message);
                    }
                })
                .template("log {percent}%")
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("log", 10, c);
        bar.start();
        bar.complete();
        TestSupport.check(!logged.isEmpty(), "logWith routes frames to lambda");
        TestSupport.check(logged.get(0).startsWith("MY-APP [PROGRESS] log "),
                "lambda can prepend own content");
        bar.close();

        final List<String> adapted = new ArrayList<String>();
        ProgressLogger logger = ProgressLoggers.of(new java.util.function.Consumer<String>() {
            @Override public void accept(String message) {
                adapted.add(message);
            }
        });
        new LoggingSink(logger).write("first\nsecond");
        TestSupport.checkEquals(2, adapted.size(), "ProgressLoggers.of adapts a lambda");
        TestSupport.checkEquals("[PROGRESS] first", adapted.get(0), "LoggingSink keeps marker");

        final List<String> customMarker = new ArrayList<String>();
        LoggingSink.of(new java.util.function.Consumer<String>() {
            @Override public void accept(String message) {
                customMarker.add(message);
            }
        }, "<< ").write("x\ny");
        TestSupport.checkEquals("<< x", customMarker.get(0), "LoggingSink.of supports custom marker");
    }

    private static void printStreamModes() throws Exception {
        ByteArrayOutputStream inlineBytes = new ByteArrayOutputStream();
        PrintStream inlineStream = new PrintStream(inlineBytes, true, "UTF-8");
        ProgressConfig inlineConfig = ProgressConfig.builder()
                .printStream(inlineStream, false)
                .template("raw {percent}%")
                .refreshInterval(0L)
                .build();
        ProgressBar inlineBar = ProgressBar.of("raw", 10, inlineConfig);
        inlineBar.start();
        inlineBar.complete();
        String raw = TestSupport.readUtf8(inlineBytes);
        TestSupport.check(raw.contains("raw 100.00%"), "PrintStreamSink inline writes frame");
        TestSupport.check(!raw.contains("\n"), "PrintStreamSink inline adds no newline");
        inlineBar.close();

        ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
        PrintStream lineStream = new PrintStream(lineBytes, true, "UTF-8");
        ProgressConfig lineConfig = ProgressConfig.builder()
                .sink(PrintStreamSink.lines(lineStream))
                .template("line {percent}%")
                .refreshInterval(0L)
                .build();
        ProgressBar lineBar = ProgressBar.of("line", 10, lineConfig);
        lineBar.start();
        lineBar.complete();
        String text = TestSupport.readUtf8(lineBytes);
        TestSupport.check(text.contains("line 100.00%" + System.lineSeparator()),
                "PrintStreamSink.lines appends newline");
        lineBar.close();
    }

    private static void customFormatters() {
        final List<String> frames = new ArrayList<String>();
        ProgressFormatter formatter = new ProgressFormatter() {
            @Override public String format(ProgressSnapshot snapshot, ProgressConfig config) {
                return "task=42 " + ProgressFormat.format(snapshot, config) + " [own suffix]";
            }
        };
        ProgressConfig c = ProgressConfig.builder()
                .sink(ConsumerSink.of(new java.util.function.Consumer<String>() {
                    @Override public void accept(String frame) {
                        frames.add(frame);
                    }
                }))
                .formatter(formatter)
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("formatted", 10, c);
        bar.start();
        bar.complete();
        TestSupport.check(frames.get(frames.size() - 1).startsWith("task=42 "),
                "custom formatter prepends own fields");
        TestSupport.check(frames.get(frames.size() - 1).endsWith("[own suffix]"),
                "custom formatter appends own fields");
        bar.close();

        String oneOff = bar.render(new ProgressFormatter() {
            @Override public String format(ProgressSnapshot snapshot, ProgressConfig config) {
                return "oneoff=" + (long) snapshot.getPercent();
            }
        });
        TestSupport.checkEquals("oneoff=100", oneOff, "one-off render with custom formatter");
    }

    private static void extraPlaceholders() {
        final List<String> frames = new ArrayList<String>();
        Map<String, String> values = new HashMap<String, String>();
        values.put("job", "J-7");
        values.put("name", "should-not-win");
        ProgressConfig c = ProgressConfig.builder()
                .sink(ConsumerSink.of(new java.util.function.Consumer<String>() {
                    @Override public void accept(String frame) {
                        frames.add(frame);
                    }
                }))
                .template("[{job}] {name} {percent}%")
                .extra("user", "alice")
                .extras(values)
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("builtin", 10, c);
        bar.start();
        bar.complete();
        String frame = frames.get(frames.size() - 1);
        TestSupport.check(frame.contains("[J-7]"), "extras() supplies custom placeholder");
        TestSupport.check(frame.contains("builtin"), "built-in placeholders take precedence");
        TestSupport.checkEquals("alice", c.getExtras().get("user"), "extra() stores value");

        ProgressConfig hostConfig = ProgressConfig.builder().build();
        ProgressSnapshot snapshot = bar.snapshot();
        String direct = ProgressFormat.format("{job}/{percent}", snapshot, hostConfig.getStyle(),
                10, 2, "", values);
        TestSupport.check(direct.startsWith("J-7/"), "low-level format accepts extras map");
        bar.close();
    }

    private static void groupFormatters() {
        final List<String> frames = new ArrayList<String>();
        ProgressConfig gc = ProgressConfig.builder()
                .sink(ConsumerSink.of(new java.util.function.Consumer<String>() {
                    @Override public void accept(String frame) {
                        frames.add(frame);
                    }
                }))
                .groupFormatter(new ProgressGroupFormatter() {
                    @Override public String format(List<ProgressSnapshot> snapshots, ProgressConfig config) {
                        return "HEAD(" + snapshots.size() + ")\n"
                                + ProgressConfig.DEFAULT_GROUP_FORMATTER.format(snapshots, config)
                                + "\nFOOT";
                    }
                })
                .refreshInterval(0L)
                .build();
        ProgressGroup group = ProgressGroup.of(gc);
        group.add("a", 10);
        group.add("b", 10);
        group.flush();
        String frame = frames.get(frames.size() - 1);
        TestSupport.check(frame.startsWith("HEAD(2)\n"), "group formatter can add a header");
        TestSupport.check(frame.endsWith("\nFOOT"), "group formatter can add a footer");
        TestSupport.check(frame.contains("[1/2]") && frame.contains("[2/2]"),
                "group formatter sees indexed snapshots");

        String oneOff = group.render(new ProgressGroupFormatter() {
            @Override public String format(List<ProgressSnapshot> snapshots, ProgressConfig config) {
                return "count=" + snapshots.size();
            }
        });
        TestSupport.checkEquals("count=2", oneOff, "one-off group render");
        group.close();
    }

    private static void pureApiRendering() {
        ProgressConfig c = ProgressConfig.builder().sink(NoopSink.INSTANCE).build();
        ProgressBar bar = ProgressBar.of("api", 10, c);
        bar.start();
        bar.update(5);
        TestSupport.checkEquals(50.0, bar.getPercent(), "pure API value still available");
        TestSupport.check(bar.render().contains("50.00%"), "render works without any output sink");
        bar.close();
    }

    private static void manualRefreshMode() {
        final AtomicInteger writes = new AtomicInteger();
        ProgressConfig c = ProgressConfig.builder()
                .sink(ConsumerSink.of(new java.util.function.Consumer<String>() {
                    @Override public void accept(String frame) {
                        writes.incrementAndGet();
                    }
                }))
                .refreshInterval(0L)
                .build();
        ProgressBar bar = ProgressBar.of("manual", 10, c);
        bar.start();
        TestSupport.checkEquals(1, writes.get(), "manual mode draws once on start");
        bar.update(5);
        TestSupport.checkEquals(1, writes.get(), "manual mode does not auto-draw on update");
        bar.flush();
        TestSupport.checkEquals(2, writes.get(), "manual mode draws on flush");
        bar.complete();
        TestSupport.checkEquals(3, writes.get(), "manual mode draws final frame");
        bar.close();
    }

    private static void invalidArguments() {
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ConsumerSink.of(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ConsumerSink.lines(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                new PrintStreamSink(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressLoggers.of(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                LoggingSink.of(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().formatter(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().groupFormatter(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().extra(null, "v");
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().extra("k", null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().extras(null);
            }
        });
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                ProgressConfig.builder().printStream(null, true);
            }
        });
        ProgressBar bar = ProgressBar.of("bad", 10, ProgressConfig.builder()
                .sink(NoopSink.INSTANCE).build());
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                bar.render((ProgressFormatter) null);
            }
        });
        ProgressGroup group = ProgressGroup.builder().config(ProgressConfig.builder()
                .sink(NoopSink.INSTANCE).build()).build();
        TestSupport.expect(IllegalArgumentException.class, new TestSupport.ThrowingRunnable() {
            @Override public void run() {
                group.render((ProgressGroupFormatter) null);
            }
        });
        bar.close();
        group.close();
    }
}
