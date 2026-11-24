# progress-bars

[English](README.md) | 中文

[![Java 8+](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://openjdk.org/)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![Dependencies](https://img.shields.io/badge/dependencies-none-brightgreen.svg)

一个零第三方依赖的 Java 进度条库：既能像 `wget`、`docker pull` 那样在控制台原地刷新，也能把每一帧送进项目自己的日志框架，还可以完全不输出、只通过 API 提供数值与渲染好的字符串。

- **JDK 8 ~ 28 兼容**：只用 JDK 8 语法与 API；`pom.xml` 使用 `source/target = 8`（不是 `release`），所以 JDK 8 自身也能构建。
- **零运行依赖**：不依赖 SLF4J、Log4j 或 JUnit；日志通过一个单方法接口桥接。
- **多进度并排**：`ProgressGroup` 管理多个任务，每行带 `[x/xx]` 前缀。
- **精度可控**：`66.67%` 两位小数，或 `67%` 整数。
- **刷新频率可控**：例如每 100ms 一次；设为 `0` 则完全手动 `flush()`。
- **异步回调**：`ProgressListener`（`onProgress` / `onComplete` / `onCancel`）与 `CompletableFuture`。
- **覆盖真实场景**：上传/下载流装饰器、固定步数执行器、未知总量 spinner、字节与速率格式化。
- **便于宿主项目集成**：可以用自己的日志、`System.out.print`、UI 回调、自定义 formatter，或者自己拿字符串处理。
- **线程安全**：多个生产线程可同时更新同一条进度。

## 目录

- [安装](#安装)
- [快速开始](#快速开始)
  - [单条进度](#单条进度)
  - [多条进度](#多条进度)
  - [日志输出](#日志输出)
  - [纯 API](#纯-api)
  - [集成到你的项目](#集成到你的项目)
- [核心 API](#核心-api)
  - [ProgressBar](#progressbar)
  - [ProgressGroup](#progressgroup)
  - [ProgressConfig](#progressconfig)
  - [输出 Sink](#输出-sink)
  - [快照与回调](#快照与回调)
- [模板与占位符](#模板与占位符)
- [真实场景](#真实场景)
- [进阶配置](#进阶配置)
- [注意事项](#注意事项)
- [许可证](#许可证)

## 安装

Maven 坐标（版本 `0.1`）：

```xml
<dependency>
  <groupId>io.github.msscn-cn</groupId>
  <artifactId>progress-bars</artifactId>
  <version>0.1</version>
</dependency>
```

Java 包名为 `cn.msscn.progress`（groupId 可以使用与包名不同的命名空间）。从源码构建：

```bash
mvn -q clean package
```

## 快速开始

### 单条进度

```java
ProgressConfig config = ProgressConfig.builder()
        .width(28)                                   // 进度条字符宽度
        .twoDecimals()                               // 66.67%
        .style(ProgressStyle.ASCII)
        .refreshInterval(80, TimeUnit.MILLISECONDS)  // 最多约 12.5 帧/秒
        .sink(ConsoleSink.auto())                    // TTY 原地刷新；管道则逐行追加
        .build();

ProgressBar bar = ProgressBar.of("app.jar", 100, config);

for (int i = 0; i <= 100; i++) {
    bar.update(i);                                   // 或 increment(1) / tick()
    Thread.sleep(20);
}
bar.close();
config.getSink().close();
```

终端效果（实际会覆盖同一行）：

```text
[1/1] app.jar [========================>            ] 80.00%
```

### 多条进度

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

// 每条可以由自己的线程推进；统一由一个刷新线程绘制，不会串行输出。
worker(kernel, 20);
worker(modules, 15);
worker(docs, 10);

group.allDone().get();       // 全部完成后得到 List<ProgressSnapshot>
group.close();
```

```text
[1/3] kernel.tar.gz  [████████████░░░░░░░░] 60.00%
[2/3] modules.tar.gz [██████░░░░░░░░░░░░░░] 30.00%
[3/3] docs.tar.gz    [████████████████████] 100.00%
```

用 `group.add(ProgressConfig override, name, total)` 可以给某一条单独设置精度、样式或模板。整组共用一个输出，因此 override 里的 sink 会被忽略。

### 日志输出

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(new CompositeSink(
                ConsoleSink.auto(),
                new LoggingSink(new JdkProgressLogger("my.progress", Level.INFO))))
        .template(ProgressFormat.COMPACT)
        .integer()                                   // 67%
        .refreshInterval(500, TimeUnit.MILLISECONDS) // 日志不宜刷太快
        .build();
```

`LoggingSink` 每行一条日志，默认加 `[PROGRESS] ` 前缀。

### 纯 API

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(NoopSink.INSTANCE)
        .refreshInterval(0, TimeUnit.MILLISECONDS)   // 连调度线程都不创建
        .build();

ProgressBar bar = ProgressBar.of("job", 200, config);
bar.update(75.5);

double value   = bar.getValue();       // 75.5
double percent = bar.getPercent();     // 37.75
ProgressSnapshot s = bar.snapshot();   // 不可变快照
String rendered = bar.render();        // 只返回字符串，不输出
```

### 集成到你的项目

库只负责产生「快照」和「文本帧」，**不占有输出通道**。以下四种方式可按需组合。

**1. 用项目自己的日志框架**（不需要依赖 SLF4J/Log4j/Logback）：

```java
ProgressConfig config = ProgressConfig.builder()
        .logWith(msg -> log.info("{}", msg))   // 任意 Consumer<String>
        .build();
```

`logWith` 底层是 `LoggingSink.of(...)`：帧按 `\n` 拆行，每行一条日志，默认带 `[PROGRESS] ` 前缀。想自己控制前缀：

```java
.sink(LoggingSink.of(msg -> log.info("{}", msg), "  "))
```

也可以显式桥接成 `ProgressLogger`：

```java
ProgressLogger bridge = ProgressLoggers.of(msg -> log.info("{}", msg));
.sink(new LoggingSink(bridge, "[P] "))
```

**2. 用 `System.out.print` 或任意 PrintStream**：

```java
.sink(ConsumerSink.of(System.out::print))     // 原始帧，不自动换行
.sink(PrintStreamSink.inline(System.out))     // 同上，专用 Sink
.sink(PrintStreamSink.lines(System.out))      // 每帧一次 println
```

真实终端仍推荐 `ConsoleSink.auto()`；上面这些适合宿主已经接管输出流的场景。

**3. 自己格式化，并插入自己的内容**：

```java
ProgressConfig config = ProgressConfig.builder()
        .template("[{job}] {name} {bar} {percent}% by={user}")
        .extra("job", "JOB-9")        // 固定值
        .extra("user", currentUser()) // 你的业务数据
        .refreshInterval(0L)          // 手动模式：只有 flush() 时才输出
        .build();
```

内置占位符优先，`extras` 只填充未知 `{key}`，不会误覆盖内置值。也可以运行时直接传 map：

```java
ProgressFormat.format(template, snapshot, config.getStyle(), 30, 2, "", myExtras);
```

如果模板也不够，直接写渲染函数：

```java
ProgressFormatter formatter = (snapshot, cfg) ->
        LocalDateTime.now() + " " + ProgressFormat.format(snapshot, cfg)
                + " job=" + jobId;

ProgressConfig.builder()
        .formatter(formatter)
        .sink(ConsumerSink.of(text -> myUi.setText(text)))
        .build();
```

整组也可以自定义（表头、表尾、汇总行）：

```java
ProgressGroupFormatter groupFormatter = (snapshots, cfg) ->
        "=== " + snapshots.size() + " tasks ===\n"
        + ProgressConfig.DEFAULT_GROUP_FORMATTER.format(snapshots, cfg)
        + "\ndone: " + countDone(snapshots);

ProgressGroup.builder()
        .config(ProgressConfig.builder().groupFormatter(groupFormatter).build())
        .build();
```

**4. 只拿值，自己打印**：

```java
ProgressBar bar = ProgressBar.of("task", 100, ProgressConfig.builder()
        .sink(NoopSink.INSTANCE).build());
bar.update(37);

String text   = bar.render();                                  // 按配置的 formatter 渲染
String custom = bar.render((s, cfg) -> "我想显示 " + s.getPercent());
ProgressSnapshot snap = bar.snapshot();                        // 数值、状态、速度、时间
List<ProgressSnapshot> all = group.snapshots();                // 整组快照
```

`render()` 只返回字符串，不写任何地方；你可以送进日志、消息队列、WebSocket 或 UI。

## 核心 API

### ProgressBar

| 方法 | 说明 |
| --- | --- |
| `of(name, total)` / `of(name)` / `of(name, total, config)` | 静态工厂；默认总量 100 |
| `unknown(name)` / `unknown(name, config)` | 未知总量：显示 spinner 而不是百分比 |
| `start()` | 进入 `RUNNING` 并画首帧；重复调用无副作用 |
| `update(value)` / `increment(delta)` / `tick()` | 绝对值、增量、+1 |
| `setTotal(total)` | 运行中改目标值，并裁剪当前值 |
| `complete()` / `cancel()` | 以 `COMPLETED` / `CANCELLED` 结束；幂等 |
| `getValue()` / `getTotal()` / `getPercent()` / `getSpeed()` | 原始数值 |
| `snapshot()` | 不可变 `ProgressSnapshot` |
| `render()` / `render(formatter)` | 只渲染字符串，不输出 |
| `flush()` | 立即重绘（手动模式必需） |
| `future()` | 结束时完成的 `CompletableFuture<ProgressSnapshot>` |
| `close()` | 停止刷新调度，不关闭 sink |

### ProgressGroup

| 方法 | 说明 |
| --- | --- |
| `of(config)` / `builder().config(config).build()` | 创建组 |
| `add(name, total)` / `add(name)` | 添加继承组配置的进度条 |
| `add(config, name, total)` | 添加带独立模板/样式/精度的进度条 |
| `addFile(File)` / `addFile(Path)` | 总量取文件长度 |
| `addUnknown(name[, config])` | 添加不确定总量（spinner）任务 |
| `items()` / `snapshots()` / `size()` | 只读视图 |
| `render()` / `render(groupFormatter)` | 把全部行渲染成一个字符串 |
| `start()` / `flush()` / `allDone()` / `close()` | 生命周期与聚合 Future |

所有变更都有同步保护，且只有一个刷新线程写 sink。

### ProgressConfig

通过 `ProgressConfig.builder()` 构建；`toBuilder()` 可复制已有配置。

| 配置 | 说明 |
| --- | --- |
| `width(int)` | 进度条字符宽度（默认 30） |
| `decimals(int)` / `integer()` / `twoDecimals()` | 百分比精度（默认 2，范围 0–6） |
| `refreshInterval(long[, TimeUnit])` / `refreshIntervalMillis(long)` | 节流间隔；`0` 表示只手动 `flush()`（默认 100ms） |
| `style(ProgressStyle)` | 字符与括号 |
| `template(String)` / `unit(String)` | 文本模板与 `{unit}` 值 |
| `sink(ProgressSink)` / `sink(PrintStream)` / `sink(Consumer<String>)` / `sinkLines(Consumer<String>)` | 帧的去向 |
| `printStream(PrintStream, boolean newline)` / `logWith(Consumer<String>)` / `logger(ProgressLogger)` | 输出快捷方式 |
| `formatter(ProgressFormatter)` / `groupFormatter(ProgressGroupFormatter)` | 替换渲染器 |
| `extra(key, value)` / `extras(Map)` | 自定义 `{key}` 占位符 |
| `listener(ProgressListener)` / `listenerExecutor(Executor)` / `callback(listener, executor)` / `noListener()` | 异步回调 |

### 输出 Sink

| 实现 | 行为 |
| --- | --- |
| `ConsoleSink.auto()` / `.interactive(out)` | ANSI 光标原地覆盖上一帧 |
| `ConsoleSink.plain(out)` | 逐行追加（管道、文件） |
| `ConsoleSink.stderr()` | 在 `System.err` 上做同样的自动检测 |
| `LoggingSink(logger[, marker])` / `LoggingSink.of(consumer[, marker])` | 每行一条日志 |
| `ConsumerSink.of(frame -> ...)` / `.lines(line -> ...)` | 把帧交给任意代码；可带 `onClose` 钩子 |
| `PrintStreamSink.inline(out)` / `.lines(out)` | 整帧 `print` 或 `println` |
| `CompositeSink(sinks...)` | 同时输出到多个 sink |
| `NoopSink.INSTANCE` | 丢弃所有输出 |

`ProgressSink` 有 `write(String)`、`flush()`、`close()` 三个方法。进度条 `close()` 只停止调度；**sink 由调用方负责关闭**。

### 快照与回调

`ProgressSnapshot` 不可变：`index`、`count`、`name`、`value`、`total`、`percent`、`speed`、`status`、`startedAtMillis`、`updatedAtMillis`、`isIndeterminate()`、`isDone()`。

```java
ProgressConfig config = ProgressConfig.builder()
        .sink(NoopSink.INSTANCE)
        .listener(new ProgressListener() {
            @Override public void onProgress(ProgressSnapshot s) { /* 每次更新 */ }
            @Override public void onComplete(ProgressSnapshot s) { /* 完成 */ }
            @Override public void onCancel(ProgressSnapshot s)   { /* 取消 */ }
        })
        .listenerExecutor(Runnable::run)   // 默认是共享 daemon 线程
        .build();

ProgressBar bar = ProgressBar.of("job", 10, config);
bar.update(10);                                   // 到达 total 自动完成
ProgressSnapshot finalState = bar.future().get(1, TimeUnit.SECONDS);
```

三个回调方法都有默认空实现，只覆写关心的一个即可。`ProgressGroup.allDone()` 在所有子任务完成后返回全部最终快照。

## 模板与占位符

未知占位符原样保留，方便发现拼写错误。

| 占位符 | 含义 |
| --- | --- |
| `{index}` / `{count}` | 组内序号（从 1 开始）/ 任务数 |
| `{name}` | 任务名 |
| `{bar}` | 进度字符段（未知总量时为 spinner） |
| `{percent}` | 百分比，不带 `%`，按 `decimals` 格式化 |
| `{value}` / `{total}` / `{unit}` | 当前值 / 总量 / 单位文本 |
| `{status}` | `PENDING`、`RUNNING`、`COMPLETED`、`CANCELLED` |
| `{speed}` / `{eta}` / `{elapsed}` | 每秒增量 / 预计剩余 / 已用时 |

内置模板：`ProgressFormat.DEFAULT`、`ProgressFormat.DETAILED`、`ProgressFormat.COMPACT`。

```java
.template("[{index}/{count}] {name} {bar} {percent}% ({value}/{total}{unit}) {speed}{unit}/s eta {eta}")
.unit("MB")
```

## 真实场景

业务代码可以只依赖 `ProgressReporter` 接口：

```java
ProgressReporter reporter = ProgressBar.of("payload.bin", contentLength, config);
reporter.start();
reporter.update(bytesRead);        // 绝对位置
reporter.increment(chunkSize);     // 增量
reporter.setTotal(newLength);      // 长度变化
reporter.complete();
```

依赖注入场景可以用 `ProgressReporter.noop()` 作为空实现。

**下载：包装 InputStream**

```java
ProgressBar bar = ProgressBar.of("response.bin", contentLength, config);
try (InputStream in = ProgressStreams.wrap(responseBody, bar)) {
    byte[] buf = new byte[8192];
    int n;
    while ((n = in.read(buf)) >= 0) {
        output.write(buf, 0, n);    // bar 自动累计已读字节
    }
}                                  // 读到 EOF 自动 complete()
```

**上传：包装 OutputStream**

```java
ProgressBar bar = ProgressBar.of("upload.bin", file.length(), config);
try (OutputStream out = ProgressStreams.wrap(requestBody, bar)) {
    Files.copy(file.toPath(), out); // bar 自动累计已写字节
}                                   // close() 时自动 complete()
```

**未知长度（chunked）**

```java
ProgressBar bar = ProgressBar.builder().name("chunked.bin").unknownTotal().config(config).build();
bar.increment(bytesRead);           // {bar} 显示 | / - \，{percent} 显示 --
bar.complete();                     // 完成时变为 100%
```

组内用 `group.addUnknown("stream.bin")` 可以实现同样效果。

**固定步数执行**

```java
ProgressBar bar = ProgressBar.of("migration", 5, config);
ProgressRunner.run(bar, 5, index -> doStep(index));
// 成功 -> complete()；抛异常 -> cancel() 并重新抛出
```

其他形式：`runUnchecked`、`forEach(reporter, items, IndexedConsumer)`、`forEach(reporter, items, Consumer)`、`runTasks(reporter, tasks)`。

**字节与速率**

```java
ByteFormat.format(1536);                // "1.5 KB"
ByteFormat.rate(2_500_000.0);           // "2.4 MB/s"
ByteFormat.renderBytes(bar.snapshot()); // "1.5 KB / 10.0 MB (512.0 KB/s)"
```

`{speed}` 是「单位/秒」；配合 `.unit("B")` 得到 `B/s`，若要自动换算的 MB/s，可用 `ByteFormat.rate(snapshot.getSpeed())` 自己拼模板。

## 进阶配置

| 需求 | API |
| --- | --- |
| 整数百分比 | `decimals(0)` / `integer()` |
| 两位小数 | `decimals(2)` / `twoDecimals()` |
| 节流刷新 | `refreshInterval(100, TimeUnit.MILLISECONDS)` |
| 只手动刷新 | `refreshInterval(0)` 后调用 `bar.flush()` / `group.flush()` |
| ASCII / 方块 / 阴影 / 井号 / 箭头 / 无括号 | `ProgressStyle.ASCII`、`BLOCK`、`SHADE`、`HASH`、`ARROW`、`PLAIN` |
| 自定义字符 | `new ProgressStyle('X', '>', '.', "{", "}")` |
| 组内单条覆盖 | `group.add(config, name, total)` |

`total` 必须是正的有限数；`update()` 会把值裁剪到 `[0, total]`，到达 `total` 自动完成。

## 注意事项

- 完成/取消时会立即绘制最终帧并停掉该条的调度；`close()` 只停止调度，`ProgressSink` 由调用方负责关闭。
- 多个独立进度条共用同一个控制台 sink 时，建议改用 `ProgressGroup`，让一个刷新线程独占终端。
- `ConsoleSink.auto()` 在 `System.console() != null` 时用 ANSI 光标原地刷新；输出被重定向时退化为逐帧追加，保证日志文件可读。
- 回调在可配置的 executor 上派发；快照是不可变对象，可安全跨线程传递。

## 许可证

Apache License 2.0，详见 <https://www.apache.org/licenses/LICENSE-2.0>。
