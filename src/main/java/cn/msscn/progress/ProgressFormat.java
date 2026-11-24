package cn.msscn.progress;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Template based text renderer.
 *
 * <p>Supported placeholders: {@code {index}}, {@code {count}}, {@code {name}},
 * {@code {bar}}, {@code {percent}}, {@code {value}}, {@code {total}},
 * {@code {unit}}, {@code {status}}, {@code {speed}}, {@code {eta}},
 * {@code {elapsed}}. Unknown placeholders are resolved from caller-supplied
 * "extras"; anything still unknown is left untouched so typos are visible.</p>
 */
public final class ProgressFormat {

    /** {@code [2/3] download.jar [=====>     ] 66.67%} */
    public static final String DEFAULT = "[{index}/{count}] {name} {bar} {percent}%";

    /** Detailed wget-like line with amount, speed and ETA. */
    public static final String DETAILED =
            "[{index}/{count}] {name} {bar} {percent}% ({value}/{total}{unit}) {speed}{unit}/s eta {eta}";

    /** No group index and no bar, just the numbers - useful for log files. */
    public static final String COMPACT = "{name}: {percent}% ({value}/{total}{unit})";

    private ProgressFormat() {
    }

    /** Render using a configuration object, including its extra placeholders. */
    public static String format(ProgressSnapshot snapshot, ProgressConfig config) {
        return format(snapshot, config, config.getExtras());
    }

    /** Render with an additional set of custom placeholders (they never override built-ins). */
    public static String format(ProgressSnapshot snapshot, ProgressConfig config,
                                Map<String, String> extras) {
        return format(config.getTemplate(), snapshot, config.getStyle(), config.getWidth(),
                config.getDecimals(), config.getUnit(), extras);
    }

    /** Render with the default template and empty unit. */
    public static String format(ProgressSnapshot snapshot, ProgressStyle style,
                                int width, int decimals) {
        return format(DEFAULT, snapshot, style, width, decimals, "");
    }

    /**
     * Renders with explicit settings and no custom placeholders.
     *
     * @param template the render template
     * @param snapshot the state to render
     * @param style the character set used for the bar
     * @param width bar width in characters
     * @param decimals percent precision
     * @param unit the unit appended to numeric values
     * @return the rendered line
     */
    public static String format(String template, ProgressSnapshot snapshot,
                                ProgressStyle style, int width, int decimals, String unit) {
        return format(template, snapshot, style, width, decimals, unit,
                Collections.<String, String>emptyMap());
    }

    /** Low-level render with custom {@code {key}} values supplied by the caller. */
    public static String format(String template, ProgressSnapshot snapshot,
                                ProgressStyle style, int width, int decimals, String unit,
                                Map<String, String> extras) {
        if (template == null || template.length() == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder(template.length() + 32);
        int cursor = 0;
        while (cursor < template.length()) {
            int open = template.indexOf('{', cursor);
            if (open < 0) {
                out.append(template, cursor, template.length());
                break;
            }
            out.append(template, cursor, open);
            int close = template.indexOf('}', open + 1);
            if (close < 0) {
                out.append(template, open, template.length());
                break;
            }
            String key = template.substring(open + 1, close);
            String value = resolve(key, snapshot, style, width, decimals, unit, extras);
            if (value == null) {
                out.append('{').append(key).append('}');
            } else {
                out.append(value);
            }
            cursor = close + 1;
        }
        String result = out.toString();
        // Avoid "--%" in indeterminate mode: a spinner has no percentage.
        return isSpinning(snapshot) ? result.replace("--%", "--") : result;
    }

    private static String resolve(String key, ProgressSnapshot s, ProgressStyle style,
                                  int width, int decimals, String unit,
                                  Map<String, String> extras) {
        if ("index".equals(key)) {
            return Integer.toString(s.getIndex());
        }
        if ("count".equals(key)) {
            return Integer.toString(s.getCount());
        }
        if ("name".equals(key)) {
            return s.getName();
        }
        if ("bar".equals(key)) {
            if (isSpinning(s)) {
                return style.getPrefix() + ProgressStyle.spinnerFrame(System.nanoTime()) + style.getSuffix();
            }
            return style.render(width, s.getPercent());
        }
        if ("percent".equals(key)) {
            return isSpinning(s) ? "--" : formatNumber(s.getPercent(), decimals);
        }
        if ("value".equals(key)) {
            return formatAmount(s.getValue());
        }
        if ("total".equals(key)) {
            return isSpinning(s) ? "?" : formatAmount(s.getTotal());
        }
        if ("unit".equals(key)) {
            return unit == null ? "" : unit;
        }
        if ("status".equals(key)) {
            return s.getStatus().name();
        }
        if ("speed".equals(key)) {
            return formatAmount(s.getSpeed());
        }
        if ("eta".equals(key)) {
            return formatEta(s);
        }
        if ("elapsed".equals(key)) {
            long start = s.getStartedAtMillis();
            long end = s.getUpdatedAtMillis();
            long millis = start > 0 && end >= start ? end - start : 0;
            return formatDuration(millis / 1000L);
        }
        return extras == null ? null : extras.get(key);
    }

    /** decimals = 0 gives integers (e.g. 67%), 2 gives 66.67%. */
    public static String formatNumber(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String formatAmount(double value) {
        if (!Double.isFinite(value)) {
            return "--";
        }
        if (value == Math.rint(value) && Math.abs(value) < 9.007199254740992E15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static boolean isSpinning(ProgressSnapshot s) {
        return s.isIndeterminate()
                && s.getStatus() != ProgressStatus.COMPLETED
                && s.getStatus() != ProgressStatus.CANCELLED;
    }

    private static String formatEta(ProgressSnapshot s) {
        if (isSpinning(s)) {
            return "--";
        }
        if (s.getStatus() == ProgressStatus.COMPLETED) {
            return "0s";
        }
        if (s.getStatus() == ProgressStatus.CANCELLED || s.getSpeed() <= 0) {
            return "--";
        }
        double remaining = s.getTotal() - s.getValue();
        if (remaining <= 0) {
            return "0s";
        }
        long seconds = (long) Math.ceil(remaining / s.getSpeed());
        return formatDuration(seconds);
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        if (seconds < 60L) {
            return seconds + "s";
        }
        if (seconds < 3600L) {
            return (seconds / 60L) + "m" + (seconds % 60L) + "s";
        }
        return (seconds / 3600L) + "h" + (seconds % 3600L / 60L) + "m" + (seconds % 60L) + "s";
    }
}
