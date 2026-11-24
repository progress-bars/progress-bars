package cn.msscn.progress;

/**
 * Character set used to draw the bar itself.
 *
 * <p>All characters are single BMP characters on purpose: this keeps width
 * calculations identical on JDK 8 and JDK 28 and avoids surrogate-pair
 * counting differences between terminals.</p>
 */
public final class ProgressStyle {

    /** {@code [========>          ]} - safe on every terminal. */
    public static final ProgressStyle ASCII = new ProgressStyle('=', '>', ' ', "[", "]");

    /** {@code [█████░░░░░]} - needs a UTF-8 terminal. */
    public static final ProgressStyle BLOCK = new ProgressStyle('█', '█', '░', "[", "]");

    /** {@code [████▒▒▒░░░]} - three-level block shading, no separate head. */
    public static final ProgressStyle SHADE = new ProgressStyle('▓', '▒', '░', "[", "]");

    /** {@code [#########.........]} */
    public static final ProgressStyle HASH = new ProgressStyle('#', '#', '.', "[", "]");

    /** {@code [>>>>......]} - similar to docker/wget style. */
    public static final ProgressStyle ARROW = new ProgressStyle('>', '>', '.', "[", "]");

    /** {@code ======      } - no brackets, like many download tools. */
    public static final ProgressStyle PLAIN = new ProgressStyle('=', '>', ' ', "", "");

    private static final char[] SPINNER = {'|', '/', '-', '\\'};

    /** Spinner frame for indeterminate progress; advances about 10 times/second. */
    public static char spinnerFrame(long nanoTime) {
        long ticks = Math.floorMod(nanoTime / 100000000L, SPINNER.length);
        return SPINNER[(int) ticks];
    }

    private final char complete;
    private final char head;
    private final char pending;
    private final String prefix;
    private final String suffix;

    /**
     * Creates a style with default {@code [} and {@code ]} delimiters.
     *
     * @param complete character for completed cells
     * @param head character for the leading edge, or {@code '\0'} for none
     * @param pending character for remaining cells
     */
    public ProgressStyle(char complete, char head, char pending) {
        this(complete, head, pending, "[", "]");
    }

    /**
     * Creates a style with explicit delimiters.
     *
     * @param complete character for completed cells
     * @param head character for the leading edge, or {@code '\0'} for none
     * @param pending character for remaining cells
     * @param prefix text before the bar, or null for none
     * @param suffix text after the bar, or null for none
     */
    public ProgressStyle(char complete, char head, char pending, String prefix, String suffix) {
        this.complete = complete;
        // head == '\0' means "do not draw a separate head character"
        this.head = head;
        this.pending = pending;
        this.prefix = prefix == null ? "" : prefix;
        this.suffix = suffix == null ? "" : suffix;
    }

    /** @return the character used for completed cells */
    public char getComplete() {
        return complete;
    }

    /** @return the leading-edge character, or {@code '\0'} if disabled */
    public char getHead() {
        return head;
    }

    /** @return the character used for remaining cells */
    public char getPending() {
        return pending;
    }

    /** @return the text drawn before the bar */
    public String getPrefix() {
        return prefix;
    }

    /** @return the text drawn after the bar */
    public String getSuffix() {
        return suffix;
    }

    /** Renders the bar segment only (no name, no percentage). */
    public String render(int width, double percent) {
        int w = Math.max(1, width);
        double p = Math.max(0.0, Math.min(100.0, percent));
        int filled = (int) Math.floor(p / 100.0 * w);
        if (filled > w) {
            filled = w;
        }

        StringBuilder sb = new StringBuilder(w + prefix.length() + suffix.length() + 2);
        sb.append(prefix);
        for (int i = 0; i < filled; i++) {
            if (head != '\0' && i == filled - 1 && filled < w) {
                sb.append(head);
            } else {
                sb.append(complete);
            }
        }
        for (int i = filled; i < w; i++) {
            sb.append(pending);
        }
        sb.append(suffix);
        return sb.toString();
    }
}
