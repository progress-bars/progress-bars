package cn.msscn.progress;

/** Immutable value object returned by the pure API and delivered to callbacks. */
public final class ProgressSnapshot {

    private final int index;
    private final int count;
    private final String name;
    private final double value;
    private final double total;
    private final double percent;
    private final double speed;
    private final ProgressStatus status;
    private final long startedAtMillis;
    private final long updatedAtMillis;
    private final boolean indeterminate;

    /**
     * Creates a determinate snapshot (no spinner state).
     *
     * @param index 1-based position in the group
     * @param count number of bars in the group
     * @param name the task name
     * @param value current progress value
     * @param total target value
     * @param percent percentage in [0, 100]
     * @param speed units per second
     * @param status lifecycle status
     * @param startedAtMillis start time in epoch millis, or 0 if unknown
     * @param updatedAtMillis last update time in epoch millis
     */
    public ProgressSnapshot(int index, int count, String name,
                            double value, double total, double percent, double speed,
                            ProgressStatus status, long startedAtMillis, long updatedAtMillis) {
        this(index, count, name, value, total, percent, speed, status,
                startedAtMillis, updatedAtMillis, false);
    }

    /**
     * Creates a snapshot, including indeterminate (spinner) state.
     *
     * @param index 1-based position in the group
     * @param count number of bars in the group
     * @param name the task name
     * @param value current progress value
     * @param total target value
     * @param percent percentage in [0, 100]
     * @param speed units per second
     * @param status lifecycle status
     * @param startedAtMillis start time in epoch millis, or 0 if unknown
     * @param updatedAtMillis last update time in epoch millis
     * @param indeterminate true when the total is unknown
     */
    public ProgressSnapshot(int index, int count, String name,
                            double value, double total, double percent, double speed,
                            ProgressStatus status, long startedAtMillis, long updatedAtMillis,
                            boolean indeterminate) {
        this.index = index;
        this.count = count;
        this.name = name;
        this.value = value;
        this.total = total;
        this.percent = percent;
        this.speed = speed;
        this.status = status;
        this.startedAtMillis = startedAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.indeterminate = indeterminate;
    }

    /** 1-based position inside a group; always 1 for a standalone bar. */
    public int getIndex() {
        return index;
    }

    /** Number of bars in the group; always 1 for a standalone bar. */
    public int getCount() {
        return count;
    }

    /** @return the task name */
    public String getName() {
        return name;
    }

    /** @return the current progress value */
    public double getValue() {
        return value;
    }

    /** @return the target value */
    public double getTotal() {
        return total;
    }

    /** Percentage in [0, 100], rounded for display by ProgressFormat. */
    public double getPercent() {
        return percent;
    }

    /** Units per second (smoothed); 0 until at least two updates happened. */
    public double getSpeed() {
        return speed;
    }

    /** @return the lifecycle status */
    public ProgressStatus getStatus() {
        return status;
    }

    /** @return the start time in epoch millis, or 0 if the bar never started */
    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    /** @return the last update time in epoch millis */
    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    /** @return true when the status is COMPLETED or CANCELLED */
    public boolean isDone() {
        return status == ProgressStatus.COMPLETED || status == ProgressStatus.CANCELLED;
    }

    /** True when the total is unknown (chunked downloads, streaming, etc.). */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    @Override
    public String toString() {
        return "ProgressSnapshot{" +
                "index=" + index + "/" + count +
                ", name='" + name + '\'' +
                ", value=" + value + "/" + total +
                ", percent=" + percent +
                ", status=" + status +
                '}';
    }
}
