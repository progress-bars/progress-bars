package cn.msscn.progress;

/**
 * Turns a snapshot into the text you want to show.
 *
 * <p>Use it when the built-in template is not enough, e.g. to prepend a
 * timestamp, add data from your own domain model, or embed the frame in an
 * application-specific format:</p>
 *
 * <pre>
 * ProgressFormatter formatter = (snapshot, config) -&gt;
 *         LocalDateTime.now() + " " + ProgressFormat.format(snapshot, config)
 *                 + " job=" + jobId;
 * </pre>
 */
@FunctionalInterface
public interface ProgressFormatter {

    /** Must not return null; an empty string simply draws nothing. */
    String format(ProgressSnapshot snapshot, ProgressConfig config);
}
