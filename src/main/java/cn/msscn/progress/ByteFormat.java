package cn.msscn.progress;

import java.util.Locale;

/** Human-readable byte counts and transfer rates for download/upload progress. */
public final class ByteFormat {

    private static final double K = 1024.0;

    private ByteFormat() {
    }

    /**
     * Formats a byte count supplied as a double, rounding to the nearest byte.
     *
     * @param bytes the byte count
     * @return a human-readable size such as {@code 1.5 KB}
     */
    public static String format(double bytes) {
        return format((long) Math.round(bytes));
    }

    /** {@code 512 B}, {@code 1.5 KB}, {@code 23.7 MB}, {@code 1.2 GB} ... */
    public static String format(long bytes) {
        double value = Math.abs(bytes);
        if (value < K) {
            return bytes + " B";
        }
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        double scaled = value;
        int i = -1;
        while (scaled >= K && i < units.length - 1) {
            scaled /= K;
            i++;
        }
        String sign = bytes < 0 ? "-" : "";
        return sign + String.format(Locale.ROOT, "%.1f %s", scaled, units[i]);
    }

    /**
     * Formats a transfer rate supplied as a long.
     *
     * @param bytesPerSecond bytes transferred per second
     * @return a readable rate such as {@code 2.3 MB/s}
     */
    public static String rate(long bytesPerSecond) {
        return rate((double) bytesPerSecond);
    }

    /** Formats a rate such as {@code 2.3 MB/s}; handy in custom formatters. */
    public static String rate(double bytesPerSecond) {
        if (!Double.isFinite(bytesPerSecond) || bytesPerSecond < 0) {
            return "--/s";
        }
        return format((long) Math.round(bytesPerSecond)) + "/s";
    }

    /** Convenience: formats only the current value. */
    public static String value(ProgressSnapshot snapshot) {
        return format(snapshot.getValue());
    }

    /** Convenience for progress templates: replaces raw numbers with a byte string. */
    public static String renderBytes(ProgressSnapshot snapshot) {
        return format((long) Math.round(snapshot.getValue()))
                + " / " + format((long) Math.round(snapshot.getTotal()))
                + " (" + rate(snapshot.getSpeed()) + ")";
    }
}
