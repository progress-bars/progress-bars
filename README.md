# progress-bars

English | [中文](README.zh-CN.md)

[![Java 8+](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)

A tiny, zero-dependency Java progress bar library. It renders `wget` / `docker pull` style lines in a terminal, writes frames to any logging framework, or stays completely silent and exposes values + rendered strings through a plain API.

- **JDK 8 to 28 compatible** — Java 8 source and API only; `pom.xml` uses `source/target = 8` (not `release`), so the project also builds with JDK 8 itself.
- **Zero runtime dependencies** — no SLF4J, Log4j or JUnit on the classpath. Logging is bridged through a one-method interface.
- **Multiple bars at once** — `ProgressGroup` renders one line per task with a `[x/xx]` prefix.
- **Configurable precision** — `66.67%` with two decimals or `67%` with integers.
- **Throttled refresh** — e.g. every 100 ms, or `0` for fully manual `flush()`.
- **Async callbacks** — `ProgressListener` (`onProgress` / `onComplete` / `onCancel`) plus `CompletableFuture`.
- **Real workloads** — upload/download stream wrappers, fixed-step runners, unknown-total spinners, byte/rate formatting.
- **Host-application friendly** — print through your own logger, `System.out.print`, a UI callback, a custom formatter, or render strings yourself.
- **Thread-safe** — multiple producer threads can update the same bar safely.

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
  - [A single bar](#a-single-bar)
  - [Multiple bars](#multiple-bars)
  - [Logging output](#logging-output)
  - [API only](#api-only)
  - [Integrating with your application](#integrating-with-your-application)
- [Core API](#core-api)
  - [ProgressBar](#progressbar)
  - [ProgressGroup](#progressgroup)
  - [ProgressConfig](#progressconfig)
  - [Output sinks](#output-sinks)
  - [Snapshots and callbacks](#snapshots-and-callbacks)
- [Templates and Placeholders](#templates-and-placeholders)
- [Real-world Scenarios](#real-world-scenarios)
- [Advanced Configuration](#advanced-configuration)
- [Notes](#notes)
- [License](#license)

## Installation

Maven coordinates (version `0.1`):

```xml
<dependency>
  <groupId>io.github.msscn-cn</groupId>
  <artifactId>progress-bars</artifactId>
  <version>0.1</version>
</dependency>
```

The Java package is `cn.msscn.progress` (the groupId may use a different namespace than the package name). Build from source with:

```bash
mvn -q clean package
```

## Quick Start

### A single bar

```java
ProgressConfig config = ProgressConfig.builder()
        .width(28)                                   // bar width in characters
        .twoDecimals()                               // 66.67%
        .style(ProgressStyle.ASCII)
        .refreshInterval(80, TimeUnit.MILLISECONDS)  // at most ~12.5 frames/s
        .sink(ConsoleSink.auto())                    // in-place on a TTY, append in pipes
        .build();

ProgressBar bar = ProgressBar.of("app.jar", 100, config);

for (int i = 0; i <= 100; i++) {
    bar.update(i);                                   // or bar.increment(1) / bar.tick()
    Thread.sleep(20);
}
bar.close();
config.getSink().close();
```

Typical terminal output (the same line is overwritten):

```text
[1/1] app.jar [========================>            ] 80.00%
```

### Multiple bars

```java
ProgressConfig config = ProgressConfig.builder()
        .width(20)
        .twoDecimals()
        .style(ProgressStyle.BLOCK)
        .refreshInterval(100, TimeUnit.MILLISECONDS)
        .sink(ConsoleSink.auto())
        .build();

ProgressGroup group = ProgressGroup.of(config);
ProgressBar kernel  = group.add("kernel.tar.gz", 200);
ProgressBar modules = group.add("modules.tar.gz", 50);
ProgressBar docs    = group.add("docs.tar.gz", 10);

// Each bar can be advanced from its own thread; one refresh thread draws all lines.
worker(kernel, 20);
worker(modules, 15);
worker(docs, 10);

group.allDone().get();       // List<ProgressSnapshot> once every bar is done
group.close();
```

```text
[1/3] kernel.tar.gz  [████████████░░░░░░░░] 60.00%
[2/3] modules.tar.gz [██████░░░░░░░░░░░░░░] 30.00%
[3/3] docs.tar.gz    [████████████████████] 100.00%
```

Use `group.add(ProgressConfig override, name, total)` to give one bar its own precision, style or template. The group owns the sink, so an override's sink is ignored.

### Logging output

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(new CompositeSink(
                ConsoleSink.auto(),
                new LoggingSink(new JdkProgressLogger("my.progress", Level.INFO))))
        .template(ProgressFormat.COMPACT)
        .integer()                                   // 67%
        .refreshInterval(500, TimeUnit.MILLISECONDS) // logs should not refresh too fast
        .build();
```

`LoggingSink` emits one log record per line and prefixes each one with `[PROGRESS] ` by default.

### API only

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(NoopSink.INSTANCE)
        .refreshInterval(0, TimeUnit.MILLISECONDS)   // no scheduler thread at all
        .build();

ProgressBar bar = ProgressBar.of("job", 200, config);
bar.update(75.5);

double value   = bar.getValue();       // 75.5
double percent = bar.getPercent();     // 37.75
ProgressSnapshot s = bar.snapshot();   // immutable value object
String rendered = bar.render();        // text only, printed nowhere
```

### Integrating with your application

The library produces snapshots and text frames. **It never owns the output channel.** Combine any of the following.

**1. Your project's logging framework** — no compile dependency on SLF4J/Log4j/Logback:

```java
ProgressConfig config = ProgressConfig.builder()
        .logWith(msg -> log.info("{}", msg))   // any Consumer<String>
        .build();
```

`logWith` delegates to `LoggingSink.of(...)`: frames are split on `\n`, one log record per line, with the default `[PROGRESS] ` prefix. Control the prefix yourself:

```java
.sink(LoggingSink.of(msg -> log.info("{}", msg), "  "))
```

Or bridge explicitly as a `ProgressLogger`:

```java
ProgressLogger bridge = ProgressLoggers.of(msg -> log.info("{}", msg));
.sink(new LoggingSink(bridge, "[P] "))
```

**2. `System.out.print` or any PrintStream:**

```java
.sink(ConsumerSink.of(System.out::print))     // raw frame, no newline
.sink(PrintStreamSink.inline(System.out))     // same, dedicated sink
.sink(PrintStreamSink.lines(System.out))      // one println per frame
```

`ConsoleSink.auto()` is still preferred for real terminals; the above are for hosts that already own the stream.

**3. Your own formatting, including your own content:**

```java
ProgressConfig config = ProgressConfig.builder()
        .template("[{job}] {name} {bar} {percent}% by={user}")
        .extra("job", "JOB-9")        // fixed value
        .extra("user", currentUser()) // your own business data
        .refreshInterval(0L)          // manual: output only when flush() is called
        .build();
```

Built-in placeholders take precedence; `extras` only fill unknown `{keys}`. You can also pass a map directly:

```java
ProgressFormat.format(template, snapshot, config.getStyle(), 30, 2, "", myExtras);
```

When a template is not enough, write the renderer:

```java
ProgressFormatter formatter = (snapshot, cfg) ->
        LocalDateTime.now() + " " + ProgressFormat.format(snapshot, cfg)
                + " job=" + jobId;

ProgressConfig.builder()
        .formatter(formatter)
        .sink(ConsumerSink.of(text -> myUi.setText(text)))
        .build();
```

Whole groups can be rendered with headers, footers and summaries:

```java
ProgressGroupFormatter groupFormatter = (snapshots, cfg) ->
        "=== " + snapshots.size() + " tasks ===\n"
        + ProgressConfig.DEFAULT_GROUP_FORMATTER.format(snapshots, cfg)
        + "\ndone: " + countDone(snapshots);

ProgressGroup.builder()
        .config(ProgressConfig.builder().groupFormatter(groupFormatter).build())
        .build();
```

**4. Just take the values and print them yourself:**

```java
ProgressBar bar = ProgressBar.of("task", 100, ProgressConfig.builder()
        .sink(NoopSink.INSTANCE).build());
bar.update(37);

String text   = bar.render();                                  // configured formatter
String custom = bar.render((s, cfg) -> "showing " + s.getPercent());
ProgressSnapshot snap = bar.snapshot();                        // values, status, speed, timings
List<ProgressSnapshot> all = group.snapshots();                // whole group
```

`render()` returns a string and writes nothing. Send it to a log, a message queue, a web socket or a UI.

## Core API

### ProgressBar

| Method | Description |
| --- | --- |
| `of(name, total)` / `of(name)` / `of(name, total, config)` | Static factories; default total is 100 |
| `unknown(name)` / `unknown(name, config)` | Unknown total: spinner instead of a percentage |
| `start()` | Move to `RUNNING` and draw the first frame; idempotent |
| `update(value)` / `increment(delta)` / `tick()` | Absolute value, relative delta, or +1 |
| `setTotal(total)` | Change target while running; clamps current value |
| `complete()` / `cancel()` | Finish with `COMPLETED` or `CANCELLED`; idempotent |
| `getValue()` / `getTotal()` / `getPercent()` / `getSpeed()` | Raw numeric values |
| `snapshot()` | Immutable `ProgressSnapshot` |
| `render()` / `render(formatter)` | Render text without printing it |
| `flush()` | Force a redraw (needed in manual mode) |
| `future()` | `CompletableFuture<ProgressSnapshot>` completed at the end |
| `close()` | Stop the refresh scheduler; the sink stays open |

### ProgressGroup

| Method | Description |
| --- | --- |
| `of(config)` / `builder().config(config).build()` | Create a group |
| `add(name, total)` / `add(name)` | Add a bar inheriting the group config |
| `add(config, name, total)` | Add a bar with its own template/style/precision |
| `addFile(File)` / `addFile(Path)` | Total is taken from the file length |
| `addUnknown(name[, config])` | Add an indeterminate (spinner) bar |
| `items()` / `snapshots()` / `size()` | Read-only views of the group |
| `render()` / `render(groupFormatter)` | Render all lines as one string |
| `start()` / `flush()` / `allDone()` / `close()` | Lifecycle and aggregate future |

All mutations are synchronized, and only one refresh thread writes to the sink.

### ProgressConfig

Built with `ProgressConfig.builder()`; `toBuilder()` copies an existing config.

| Setting | Meaning |
| --- | --- |
| `width(int)` | Bar width in characters (default 30) |
| `decimals(int)` / `integer()` / `twoDecimals()` | Percent precision (default 2, range 0–6) |
| `refreshInterval(long[, TimeUnit])` / `refreshIntervalMillis(long)` | Throttle interval; `0` means manual `flush()` only (default 100 ms) |
| `style(ProgressStyle)` | Character set and brackets |
| `template(String)` / `unit(String)` | Text template and `{unit}` value |
| `sink(ProgressSink)` / `sink(PrintStream)` / `sink(Consumer<String>)` / `sinkLines(Consumer<String>)` | Where frames go |
| `printStream(PrintStream, boolean newline)` / `logWith(Consumer<String>)` / `logger(ProgressLogger)` | Output shortcuts |
| `formatter(ProgressFormatter)` / `groupFormatter(ProgressGroupFormatter)` | Replace the renderer |
| `extra(key, value)` / `extras(Map)` | Custom `{key}` placeholders |
| `listener(ProgressListener)` / `listenerExecutor(Executor)` / `callback(listener, executor)` / `noListener()` | Async callbacks |

### Output sinks

| Implementation | Behavior |
| --- | --- |
| `ConsoleSink.auto()` / `.interactive(out)` | ANSI cursor movement: overwrite previous frames in place |
| `ConsoleSink.plain(out)` | Append every frame line (pipes, files) |
| `ConsoleSink.stderr()` | Same auto-detection on `System.err` |
| `LoggingSink(logger[, marker])` / `LoggingSink.of(consumer[, marker])` | One log record per line |
| `ConsumerSink.of(frame -> ...)` / `.lines(line -> ...)` | Hand frames to arbitrary code; optional `onClose` hook |
| `PrintStreamSink.inline(out)` / `.lines(out)` | `print` or `println` the whole frame |
| `CompositeSink(sinks...)` | Fan out to several sinks |
| `NoopSink.INSTANCE` | Discard all output |

`ProgressSink` has `write(String)`, `flush()` and `close()`. Bars stop their scheduler on `close()`; **the caller owns and closes the sink**.

### Snapshots and callbacks

`ProgressSnapshot` is immutable: `index`, `count`, `name`, `value`, `total`, `percent`, `speed`, `status`, `startedAtMillis`, `updatedAtMillis`, `isIndeterminate()`, `isDone()`.

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(NoopSink.INSTANCE)
        .listener(new ProgressListener() {
            @Override public void onProgress(ProgressSnapshot s) { /* each update */ }
            @Override public void onComplete(ProgressSnapshot s) { /* finished */ }
            @Override public void onCancel(ProgressSnapshot s)   { /* cancelled */ }
        })
        .listenerExecutor(Runnable::run)   // default: shared daemon thread
        .build();

ProgressBar bar = ProgressBar.of("job", 10, config);
bar.update(10);                                   // reaching total completes automatically
ProgressSnapshot finalState = bar.future().get(1, TimeUnit.SECONDS);
```

All three listener methods have default no-op implementations, so override only what you need. `ProgressGroup.allDone()` returns all final snapshots once every child is done.

## Templates and Placeholders

Unknown placeholders are left untouched, which makes typos easy to spot.

| Placeholder | Meaning |
| --- | --- |
| `{index}` / `{count}` | 1-based position in group / number of bars |
| `{name}` | Task name |
| `{bar}` | Rendered bar characters (spinner for unknown total) |
| `{percent}` | Percentage without `%`, formatted by `decimals` |
| `{value}` / `{total}` / `{unit}` | Current value / target / unit text |
| `{status}` | `PENDING`, `RUNNING`, `COMPLETED`, `CANCELLED` |
| `{speed}` / `{eta}` / `{elapsed}` | Units per second / estimated remaining / elapsed time |

Built-in templates: `ProgressFormat.DEFAULT`, `ProgressFormat.DETAILED`, `ProgressFormat.COMPACT`.

```java
.template("[{index}/{count}] {name} {bar} {percent}% ({value}/{total}{unit}) {speed}{unit}/s eta {eta}")
.unit("MB")
```

## Real-world Scenarios

Business code can depend only on the `ProgressReporter` interface:

```java
ProgressReporter reporter = ProgressBar.of("payload.bin", contentLength, config);
reporter.start();
reporter.update(bytesRead);        // absolute position
reporter.increment(chunkSize);     // delta
reporter.setTotal(newLength);      // length changed
reporter.complete();
```

Or use `ProgressReporter.noop()` as a null object in dependency-injected code.

**Download — wrap an InputStream:**

```java
ProgressBar bar = ProgressBar.of("response.bin", contentLength, config);
try (InputStream in = ProgressStreams.wrap(responseBody, bar)) {
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) >= 0) {
        output.write(buf, 0, n);    // bar counts bytes read automatically
    }
}                                  // EOF completes the bar
```

**Upload — wrap an OutputStream:**

```java
ProgressBar bar = ProgressBar.of("upload.bin", file.length(), config);
try (OutputStream out = ProgressStreams.wrap(requestBody, bar)) {
    Files.copy(file.toPath(), out); // bar counts bytes written automatically
}                                   // close() completes the bar
```

**Unknown length (chunked encoding):**

```java
ProgressBar bar = ProgressBar.builder().name("chunked.bin").unknownTotal().config(config).build();
bar.increment(bytesRead);           // {bar} shows | / - \, {percent} shows --
bar.complete();                     // switches to 100%
```

`group.addUnknown("stream.bin")` does the same inside a group.

**Fixed-step execution:**

```java
ProgressBar bar = ProgressBar.of("migration", 5, config);
ProgressRunner.run(bar, 5, index -> doStep(index));
// success -> complete(); exception -> cancel() and rethrow
```

Alternatives: `runUnchecked`, `forEach(reporter, items, IndexedConsumer)`, `forEach(reporter, items, Consumer)` and `runTasks(reporter, tasks)`.

**Bytes and rates:**

```java
ByteFormat.format(1536);                // "1.5 KB"
ByteFormat.rate(2_500_000.0);           // "2.4 MB/s"
ByteFormat.renderBytes(bar.snapshot()); // "1.5 KB / 10.0 MB (512.0 KB/s)"
```

`{speed}` prints `units/second`; combine it with `.unit("B")` for `B/s`, or build your own template with `ByteFormat.rate(snapshot.getSpeed())`.

## Advanced Configuration

| Need | API |
| --- | --- |
| Integer percent | `decimals(0)` / `integer()` |
| Two decimals | `decimals(2)` / `twoDecimals()` |
| Throttle redraws | `refreshInterval(100, TimeUnit.MILLISECONDS)` |
| Manual redraws only | `refreshInterval(0)` then `bar.flush()` / `group.flush()` |
| ASCII / block / shade / hash / arrow / plain | `ProgressStyle.ASCII`, `BLOCK`, `SHADE`, `HASH`, `ARROW`, `PLAIN` |
| Custom characters | `new ProgressStyle('X', '>', '.', "{", "}")` |
| Per-bar override in a group | `group.add(config, name, total)` |

`total` must be a positive finite number; `update()` clamps values to `[0, total]` and reaching `total` completes the bar automatically.

## Notes

- On completion or cancellation the final frame is drawn immediately and the scheduler for that bar stops. `close()` only stops the scheduler; close the `ProgressSink` yourself.
- If several independent bars share one console sink, prefer a `ProgressGroup` so a single refresh thread owns the terminal.
- `ConsoleSink.auto()` uses ANSI cursor movement when `System.console() != null`; with redirected output it degrades to appending frames so log files remain readable.
- Callbacks are dispatched on a configurable executor; always treat snapshots as immutable.

## License

Apache License 2.0. See <https://www.apache.org/licenses/LICENSE-2.0>.
