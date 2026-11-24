package cn.msscn.progress;

import java.util.List;

/**
 * Renders an entire group as one frame, so you can add headers, footers or
 * summary lines of your own around the individual progress lines.
 */
@FunctionalInterface
public interface ProgressGroupFormatter {

    /** Snapshots are in display order; the list is unmodifiable. */
    String format(List<ProgressSnapshot> snapshots, ProgressConfig config);
}
