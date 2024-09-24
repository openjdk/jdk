package java.util;

/**
 * Represents a range of values.
 *
 * @param <T> the type of the values
 */
public abstract sealed class Range<T extends Comparable<T>> {
    /**
     * Returns an unbounded range.
     *
     * @param <T> the type of the values
     * @return an unbounded range
     */
    public static <T extends Comparable<T>> Range<T> unbounded() {
        return new UnboundedRange<>();
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end the end of the range
     * @param <T> the type of the values
     * @return an unbounded range ending at the given value
     */
    public static <T extends Comparable<T>> Range<T> unboundedEndingAt(T end) {
        return new UnboundedStartRange<>(end);
    }

    /**
     * Returns an unbounded range ending at the given value.
     *
     * @param end the end of the range
     * @param excludeEnd whether the end is exclusive
     * @param <T> the type of the values
     * @return an unbounded range ending at the given value
     */
    public static <T extends Comparable<T>> Range<T> unboundedEndingAt(T end, boolean excludeEnd) {
        return new UnboundedStartRange<>(end, excludeEnd);
    }

    /**
     * Returns an unbounded range starting at the given value with the start included.
     *
     * @param start the start of the range
     * @param <T>   the type of the values
     * @return an unbounded range starting at the given value
     */
    public static <T extends Comparable<T>> Range<T> unboundedStartingAt(T start) {
        return new UnboundedEndRange<>(start);
    }

    /**
     * Returns an unbounded range starting at the given value.
     * @param start the start of the range
     * @param excludeStart whether the start is exclusive
     * @return an unbounded range starting at the given value
     * @param <T> the type of the values
     */
    public static <T extends Comparable<T>> Range<T> unboundedStartingAt(T start, boolean excludeStart) {
        return new UnboundedEndRange<>(start, excludeStart);
    }

    /**
     * Returns a bounded range with both bounds included.
     *
     * @param start the start of the range
     * @param end   the end of the range
     * @param <T>   the type of the values
     * @return a bounded range
     */
    public static <T extends Comparable<T>> Range<T> of(T start, T end) {
        return Range.of(start, end, BoundsExclusion.NONE);
    }

    /**
     * Returns a bounded range with the given bounds exclusion.
     *
     * @param start          the start of the range
     * @param end            the end of the range
     * @param boundsExclusion the bounds exclusion
     * @param <T>            the type of the values
     * @return a bounded range with the given bounds exclusion
     */
    public static <T extends Comparable<T>> Range<T> of(T start, T end, BoundsExclusion boundsExclusion) {
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Start must be less than or equal to end");
        }
        return new BoundedRange<>(start, end, boundsExclusion);
    }

    /**
     * Returns the start of the span (inclusive).
     *
     * @return the start of the span
     * @throws UnsupportedOperationException if the span is unbounded at the start. This can be checked with the {@link #isBoundedAtStart()} method.
     */
    public abstract T start();

    /**
     * Returns the end of the span (inclusive).
     *
     * @return the end of the span
     * @throws UnsupportedOperationException if the span is unbounded at the end. This can be checked with the {@link #isBoundedAtEnd()} method.
     */
    public abstract T end();

    /**
     * Returns whether the span contains the given instant.
     *
     * @param instant the instant to check
     * @return true if the span contains the instant, false otherwise
     */
    public abstract boolean contains(T instant);

    /**
     * Returns whether the span overlaps with the given span.
     *
     * @param other the span to check
     * @return true if the span overlaps with the given span, false otherwise
     */
    public abstract boolean overlaps(Range<? extends T> other);

    /**
     * Returns whether the span is before the given span.
     *
     * @param other the span to check
     * @return true if the span is before the given span, false otherwise
     */
    public abstract boolean isBefore(Range<? extends T> other);

    /**
     * Returns whether the span is before the given point.
     *
     * @param point the instant to check
     * @return true if the span is before the given instant, false otherwise
     */
    public abstract boolean isBefore(T point);

    /**
     * Returns whether the span is after the given span.
     *
     * @param other the span to check
     * @return true if the span is after the given span, false otherwise
     */
    public abstract boolean isAfter(Range<? extends T> other);

    /**
     * Returns whether the span is after the given point.
     *
     * @param point the instant to check
     * @return true if the span is after the given instant, false otherwise
     */
    public abstract boolean isAfter(T point);

    /**
     * Returns whether the span is bounded at the start.
     * This method returning true guarantees that the {@link #start()} method will not throw an {@code UnsupportedOperationException},
     * while returning false guarantees that the {@link #start()} method will throw an {@code UnsupportedOperationException}.
     *
     * @return true if the span is bounded at the start, false otherwise
     */
    public abstract boolean isBoundedAtStart();

    /**
     * Returns whether the span is bounded at the end.
     * This method returning true guarantees that the {@link #end()} method will not throw an {@code UnsupportedOperationException},
     * while returning false guarantees that the {@link #end()} method will throw an {@code UnsupportedOperationException}.
     *
     * @return true if the span is bounded at the end, false otherwise
     */
    public abstract boolean isBoundedAtEnd();

    /**
     * Returns the intersection of the span with the given span.
     *
     * @param other the span to intersect with
     * @return the intersection of the span with the given span. If the spans do not overlap, the result is an empty optional.
     */
    public abstract Optional<Range<? extends T>> intersection(Range<? extends T> other);

    /**
     * Returns the union of the span with the given span.
     *
     * @param other the span to union with
     * @return the union of the span with the given span.
     * If the spans do not overlap, the result is an array of two spans.
     * If the spans overlap, the result is an array of one combined span.
     */
    public abstract Union<? extends T> union(Range<? extends T> other);

    /**
     * Returns the gap between the span and the given span.
     *
     * @param other the span to gap with
     * @return the gap between the span and the given span. If the spans overlap, the result is an empty optional.
     */
    public abstract Optional<Range<T>> gap(Range<? extends T> other);

    public abstract boolean isEndExclusive();

    public abstract boolean isStartExclusive();

    protected abstract int endCompareOffset();

    protected abstract int startCompareOffset();

    protected boolean startBeforeOrAtEndOf(Range<? extends T> other) {
        return !startAfterEndOf(other);
    }

    protected boolean endAfterOrAtStartOf(Range<? extends T> other) {
        return !endBeforeStartOf(other);
    }

    protected boolean startBeforeOrAtStartOf(Range<? extends T> other) {
        return !startAfterStartOf(other);
    }

    protected boolean endAfterOrAtEndOf(Range<? extends T> other) {
        return !endBeforeEndOf(other);
    }

    protected boolean endBeforeOrAtStartOf(Range<? extends T> other) {
        return !endAfterStartOf(other);
    }

    protected boolean startAfterOrAtEndOf(Range<? extends T> other) {
        return !startBeforeEndOf(other);
    }

    protected boolean startAfterOrAtStartOf(Range<? extends T> other) {
        return !startBeforeStartOf(other);
    }

    protected boolean endBeforeOrAtEndOf(Range<? extends T> other) {
        return !endAfterEndOf(other);
    }

    // this set of methods models following matrix of offsets:
    // | this  | other | result |
    // |-------|-------|--------|
    // |   0   |   0   |   1    |
    // |   0   |   1   |   1    |
    // |   1   |   0   |   0    |
    // |   1   |   1   |   1    |
    // where values in this and other are whether bound is exclusive or not
    // and result is offset for comparison

    @SuppressWarnings("unchecked")
    protected boolean endBeforeEndOf(Range<? extends T> other) {
        return end().compareTo(other.end()) <= -(1 ^ endCompareOffset() | other.endCompareOffset());
    }

    @SuppressWarnings("unchecked")
    protected boolean startBeforeStartOf(Range<? extends T> other) {
        return start().compareTo(other.start()) <= -(1 ^ startCompareOffset() | other.startCompareOffset());
    }

    @SuppressWarnings("unchecked")
    protected boolean startAfterStartOf(Range<? extends T> other) {
        return start().compareTo(other.start()) >= (startCompareOffset() | (1 ^ other.startCompareOffset()));
    }

    @SuppressWarnings("unchecked")
    protected boolean endAfterEndOf(Range<? extends T> other) {
        return end().compareTo(other.end()) >= (endCompareOffset() | (1 ^ other.endCompareOffset()));
    }

    @SuppressWarnings("unchecked")
    protected boolean startAfterEndOf(Range<? extends T> other) {
        return start().compareTo(other.end()) >= (1 ^ (startCompareOffset() | other.endCompareOffset()));
    }

    @SuppressWarnings("unchecked")
    protected boolean endBeforeStartOf(Range<? extends T> other) {
        return end().compareTo(other.start()) <= -(1 ^ (endCompareOffset() | other.startCompareOffset()));
    }

    @SuppressWarnings("unchecked")
    protected boolean startBeforeEndOf(Range<? extends T> other) {
        return start().compareTo(other.end()) < 0;
    }

    @SuppressWarnings("unchecked")
    protected boolean endAfterStartOf(Range<? extends T> other) {
        return end().compareTo(other.start()) > 0;
    }

    private static final class UnboundedRange<T extends Comparable<T>> extends Range<T> {

        @Override
        public T start() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        @Override
        public T end() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        @Override
        public boolean contains(T instant) {
            return true;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            return true;
        }

        @Override
        public boolean isBefore(Range<? extends T> other) {
            return false;
        }

        @Override
        public boolean isBefore(T point) {
            return false;
        }

        @Override
        public boolean isAfter(Range<? extends T> other) {
            return false;
        }

        @Override
        public boolean isAfter(T point) {
            return false;
        }

        @Override
        public boolean isBoundedAtStart() {
            return false;
        }

        @Override
        public boolean isBoundedAtEnd() {
            return false;
        }

        @Override
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            return Optional.of(other);
        }

        @Override
        public Union<T> union(Range<? extends T> other) {
            return new UnionOfOne<>(this);
        }

        @Override
        public Optional<Range<T>> gap(Range<? extends T> other) {
            return Optional.empty();
        }

        @Override
        public boolean isEndExclusive() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        @Override
        public boolean isStartExclusive() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        @Override
        protected int endCompareOffset() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        @Override
        protected int startCompareOffset() {
            throw new UnsupportedOperationException("Unbounded range");
        }

        public boolean equals(Object obj) {
            return obj instanceof UnboundedRange;
        }

        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "inf - inf";
        }
    }

    private static final class UnboundedStartRange<T extends Comparable<T>> extends Range<T> {

        private final T end;
        private final boolean excludeEnd;

        public UnboundedStartRange(T end) {
            this(end, false);
        }

        public UnboundedStartRange(T end, boolean excludeEnd) {
            this.end = end;
            this.excludeEnd = excludeEnd;
        }

        @Override
        public T start() {
            throw new UnsupportedOperationException("Unbounded start range");
        }

        @Override
        public T end() {
            return end;
        }

        @Override
        public boolean contains(T instant) {
            return instant.compareTo(end) <= -endCompareOffset();
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return true;
            }
            if (other.isBoundedAtStart()) {
                return endAfterOrAtStartOf(other);
            }
            return true;
        }

        @Override
        public boolean isBefore(Range<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        public boolean isAfter(Range<? extends T> other) {
            return false;
        }

        @Override
        public boolean isAfter(T point) {
            return end.compareTo(point) > 0;
        }

        @Override
        public boolean isBoundedAtStart() {
            return false;
        }

        @Override
        public boolean isBoundedAtEnd() {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return Optional.of(this);
            }
            if (other.isBoundedAtStart()) {
                if (endBeforeStartOf(other)) {
                    return Optional.empty();
                }
                if (other.isBoundedAtEnd() && endAfterOrAtEndOf(other)) {
                    // other is fully contained
                    return Optional.of(other);
                }
                return Optional.of(new BoundedRange<>(other.start(), end));
            } else if (other.isBoundedAtEnd() && endAfterOrAtEndOf(other)) {
                return Optional.of(other);
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return new UnionOfOne<T>(new UnboundedRange<>());
            }
            if (other.isBoundedAtEnd()) {
                if (endBeforeOrAtEndOf(other)) {
                    return new UnionOfOne<>(new UnboundedStartRange<>(other.end()));
                }
            } else if (other.isBoundedAtStart()) {
                if (endAfterOrAtStartOf(other)) {
                    return new UnionOfOne<T>(new UnboundedRange<>());
                }
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return Optional.empty();
            }
            if (other.isBoundedAtStart()) {
                var compResult = end.compareTo(other.start());
                if (compResult > 0 || (compResult == 0 && excludeEnd && other.isStartExclusive())) {
                    return Optional.of(new BoundedRange<>(end, other.start()));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean isEndExclusive() {
            return excludeEnd;
        }

        @Override
        public boolean isStartExclusive() {
            throw new UnsupportedOperationException("Unbounded start range");
        }

        @Override
        protected int endCompareOffset() {
            return excludeEnd ? 1 : 0;
        }

        @Override
        protected int startCompareOffset() {
            throw new UnsupportedOperationException("Unbounded start range");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UnboundedStartRange<?> that)) {
                return false;
            }
            return Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(end);
        }

        @Override
        public String toString() {
            return "inf - " + end;
        }
    }

    private static final class UnboundedEndRange<T extends Comparable<T>> extends Range<T> {

        private final T start;

        private final boolean excludeStart;

        public UnboundedEndRange(T start) {
            this(start, false);
        }

        public UnboundedEndRange(T start, boolean excludeStart) {
            this.start = start;
            this.excludeStart = excludeStart;
        }

        @Override
        public T start() {
            return start;
        }

        @Override
        public T end() {
            throw new UnsupportedOperationException("Unbounded end range");
        }

        @Override
        public boolean contains(T instant) {
            return instant.compareTo(start) >= (startCompareOffset());
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return true;
            }
            if (other.isBoundedAtEnd()) {
                return startBeforeOrAtEndOf(other);
            }
            return true;
        }

        @Override
        public boolean isBefore(Range<? extends T> other) {
            return false;
        }

        @Override
        public boolean isBefore(T point) {
            return start.compareTo(point) >= 0;
        }

        @Override
        public boolean isAfter(Range<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) < 0;
        }

        @Override
        public boolean isBoundedAtStart() {
            return true;
        }

        @Override
        public boolean isBoundedAtEnd() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    if (startAfterEndOf(other)) {
                        return Optional.empty();
                    } else if (startBeforeOrAtStartOf(other)) {
                        return Optional.of(other);
                    }
                } else if (startAfterEndOf(other)) {
                    return Optional.empty();
                }
                return Optional.of(new BoundedRange<>(start, other.end()));
            } else if (other.isBoundedAtStart()) {
                if (startBeforeOrAtStartOf(other)) {
                    return Optional.of(other);
                }
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    if (startBeforeOrAtEndOf(other)) {
                        if (startAfterOrAtStartOf(other)) {
                            return new UnionOfOne<>(new UnboundedEndRange<>(other.start()));
                        }
                        return new UnionOfOne<>(this);
                    }
                } else if (startBeforeOrAtEndOf(other)) {
                    return new UnionOfOne<>(new UnboundedRange<T>());
                }
            } else if (other.isBoundedAtStart()) {
                return startAfterStartOf(other) ? new UnionOfOne<>((Range<T>) other) : new UnionOfOne<>(this);
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other instanceof Range.UnboundedRange) {
                return Optional.empty();
            }
            if (other.isBoundedAtEnd()) {
                var compResult = start.compareTo(other.end());
                if (compResult < 0 || (compResult == 0 && excludeStart && other.isEndExclusive())) {
                    return Optional.of(new BoundedRange<>(other.end(), start));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean isEndExclusive() {
            throw new UnsupportedOperationException("Unbounded end range");
        }

        @Override
        public boolean isStartExclusive() {
            return excludeStart;
        }

        @Override
        protected int endCompareOffset() {
            throw new UnsupportedOperationException("Unbounded end range");
        }

        @Override
        protected int startCompareOffset() {
            return excludeStart ? 1 : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UnboundedEndRange<?> that)) {
                return false;
            }
            return Objects.equals(start, that.start);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(start);
        }

        @Override
        public String toString() {
            return start + " - inf";
        }
    }

    private static final class BoundedRange<T extends Comparable<T>> extends Range<T> {

        private final T start;
        private final T end;
        private final boolean excludeStart;
        private final boolean excludeEnd;

        public BoundedRange(T start, T end) {
            this(start, end, BoundsExclusion.NONE);
        }

        public BoundedRange(T start, T end, BoundsExclusion boundsExclusion) {
            this.start = start;
            this.end = end;
            this.excludeStart = boundsExclusion == BoundsExclusion.START || boundsExclusion == BoundsExclusion.BOTH;
            this.excludeEnd = boundsExclusion == BoundsExclusion.END || boundsExclusion == BoundsExclusion.BOTH;
        }

        @Override
        public T start() {
            return start;
        }

        @Override
        public T end() {
            return end;
        }

        @Override
        public boolean contains(T instant) {
            return instant.compareTo(start) >= 0 && instant.compareTo(end) <= 0;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    return endAfterOrAtStartOf(other) && startBeforeOrAtEndOf(other);
                }
                return startBeforeOrAtEndOf(other);
            } else if (other.isBoundedAtStart()) {
                return endAfterOrAtStartOf(other);
            }
            return true;
        }

        @Override
        public boolean isBefore(Range<? extends T> other) {
            return other.isBoundedAtStart() && endBeforeStartOf(other);
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        public boolean isAfter(Range<? extends T> other) {
            return other.isBoundedAtEnd() && startAfterEndOf(other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) > 0;
        }

        @Override
        public boolean isBoundedAtStart() {
            return true;
        }

        @Override
        public boolean isBoundedAtEnd() {
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    if (endBeforeStartOf(other) || startAfterEndOf(other)) {
                        return Optional.empty();
                    } else if (endAfterOrAtEndOf(other)) {
                        if (startBeforeOrAtStartOf(other)) {
                            return Optional.of(other);
                        } else if (startBeforeEndOf(other)) {
                            return Optional.of(new BoundedRange<>(start, other.end()));
                        } else {
                            return Optional.empty();
                        }
                    } else {
                        if (startBeforeOrAtStartOf(other)) {
                            return Optional.of(new BoundedRange<>(other.start(), end));
                        }
                        return Optional.of(this);
                    }
                } else if (endBeforeStartOf(other)) {
                    return Optional.empty();
                }
            } else if (other.isBoundedAtEnd()) {
                if (startAfterEndOf(other)) {
                    return Optional.empty();
                }
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd() && endAfterOrAtStartOf(other)) {
                    if (other.start().compareTo(start) >= 0) {
                        return new UnionOfOne<>((Range<T>) other);
                    }
                    return new UnionOfOne<>(new BoundedRange<>(other.start(), end));
                } else {
                    if (startAfterOrAtStartOf(other)) {
                        return new UnionOfOne<>((Range<T>) other);
                    } else if (other.start().compareTo(end) <= 0) {
                        return new UnionOfOne<>(new UnboundedEndRange<>(other.start()));
                    }
                }
            } else if (other.isBoundedAtEnd()) {
                if (endBeforeOrAtEndOf(other)) {
                    return new UnionOfOne<>((Range<T>) other);
                } else if (startBeforeOrAtEndOf(other)) {
                    return new UnionOfOne<>(new UnboundedStartRange<>(other.end()));
                }
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other.isBoundedAtStart() && endBeforeStartOf(other)) {
                return Optional.of(new BoundedRange<>(end, other.start()));
            } else if (other.isBoundedAtEnd() && startAfterEndOf(other)) {
                return Optional.of(new BoundedRange<>(other.end(), start));
            }
            return Optional.empty();
        }

        @Override
        public boolean isEndExclusive() {
            return excludeEnd;
        }

        @Override
        public boolean isStartExclusive() {
            return excludeStart;
        }

        @Override
        protected int endCompareOffset() {
            return excludeEnd ? 1 : 0;
        }

        @Override
        protected int startCompareOffset() {
            return excludeStart ? 1 : 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BoundedRange<?> that)) {
                return false;
            }
            return Objects.equals(start, that.start) && Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }

        @Override
        public String toString() {
            return start + " - " + end;
        }
    }

    /**
     * Represents a union of two spans.
     * The spans are guaranteed to either abut or not touch at all.
     *
     * @param <T> the type of the values
     */
    public sealed interface Union<T extends Comparable<T>> {
    }

    /**
     * Represents a result of union operation where the result is a single span.
     *
     * @param <T> the type of the values
     */
    public static final class UnionOfOne<T extends Comparable<T>> implements Union<T> {

        private final Range<T> range;

        private UnionOfOne(Range<T> range) {
            this.range = range;
        }

        /**
         * Returns the span.
         *
         * @return the span
         */
        public Range<T> range() {
            return range;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UnionOfOne<?> that)) {
                return false;
            }
            return Objects.equals(range, that.range);
        }
    }

    /**
     * Represents a result of union operation where the result is two spans.
     *
     * @param <T> the type of the values
     */
    public static final class UnionOfTwo<T extends Comparable<T>> implements Union<T> {

        private final Range<T> first;
        private final Range<T> second;

        private UnionOfTwo(Range<T> first, Range<T> second) {
            this.first = first;
            this.second = second;
        }

        /**
         * Returns the first span.
         *
         * @return the first span
         */
        public Range<T> first() {
            return first;
        }

        /**
         * Returns the second span.
         *
         * @return the second span
         */
        public Range<T> second() {
            return second;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof UnionOfTwo<?> that)) {
                return false;
            }
            return Objects.equals(first, that.first) && Objects.equals(second, that.second);
        }
    }

    public enum BoundsExclusion {
        NONE,
        START,
        END,
        BOTH
    }
}
