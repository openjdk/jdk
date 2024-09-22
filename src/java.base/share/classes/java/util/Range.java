package java.util;

/**
 * Represents a range of values.
 * @param <T> the type of the values
 */
public abstract sealed class Range<T extends Comparable<T>> {

    public static <T extends Comparable<T>> Range<T> unbounded() {
        return new UnboundedRange<>();
    }

    public static <T extends Comparable<T>> Range<T> unboundedEndingAt(T end) {
        return new UnboundedStartRange<>(end);
    }

    public static <T extends Comparable<T>> Range<T> unboundedStartAt(T start) {
        return new UnboundedEndRange<>(start);
    }

    public static <T extends Comparable<T>> Range<T> of(T start, T end) {
        return new BoundedRange<>(start, end);
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
     * Returns whether the span is negative.
     * A span is considered negative if the start is after the end.
     *
     * @return true if the span is negative, false otherwise
     */
    public abstract boolean isNegative();

    /**
     * Returns the intersection of the span with the given span.
     *
     * @param other the span to intersect with
     * @return the intersection of the span with the given span. If the spans do not overlap, the result is an empty optional.
     */
    public abstract Optional<Range<? extends T>> intersection(Range<? extends T> other);

    protected Optional<Range<? extends T>> intersectionBoundedWithHalfUnbounded(
        Range<? extends T> other,
        T bound,
        boolean flip
    ) {
        var otherEnd = flip != other.isNegative() ? other.start() : other.end();
        if (otherEnd.compareTo(bound) < 0) {
            return Optional.empty();
        } else {
            var otherStart = other.isNegative() ? other.end() : other.start();
            if (otherStart.compareTo(bound) > 0) {
                return Optional.of(other);
            } else {
                return Optional.of(new BoundedRange<>(bound, otherEnd));
            }
        }
    }

    /**
     * Returns the union of the span with the given span.
     *
     * @param other the span to union with
     * @return the union of the span with the given span.
     * If the spans do not overlap, the result is an array of two spans.
     * If the spans overlap, the result is an array of one combined span.
     */
    public abstract Range<T>[] union(Range<? extends T> other);

    /**
     * Returns the gap between the span and the given span.
     *
     * @param other the span to gap with
     * @return the gap between the span and the given span. If the spans overlap, the result is an empty optional.
     */
    public abstract Optional<Range<T>> gap(Range<? extends T> other);

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
        public boolean isNegative() {
            return false;
        }

        @Override
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            return Optional.of(other);
        }

        @Override
        public Range<T>[] union(Range<? extends T> other) {
            throw new UnsupportedOperationException("Waiting to be redesigned");
        }

        @Override
        public Optional<Range<T>> gap(Range<? extends T> other) {
            return Optional.empty();
        }

        public boolean equals(Object obj) {
            return obj instanceof UnboundedRange;
        }

        public int hashCode() {
            return 0;
        }
    }

    private static final class UnboundedStartRange<T extends Comparable<T>> extends Range<T> {

        private final T end;

        public UnboundedStartRange(T end) {
            this.end = end;
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
            return instant.compareTo(end) <= 0;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    var otherStart = other.isNegative() ? other.end() : other.start();
                    return otherStart.compareTo(end) <= 0;
                } else {
                    return other.end().compareTo(end) <= 0;
                }
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
        public boolean isNegative() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    return intersectionBoundedWithHalfUnbounded(other, end, true);
                } else {
                    if (other.start().compareTo(end) > 0) {
                        return Optional.empty();
                    } else {
                        return Optional.of(new BoundedRange<>(other.start(), end));
                    }
                }
            } else if (other.isBoundedAtEnd()) {
                if (other.end().compareTo(end) < 0) {
                    return Optional.of(other);
                }
            }
            return Optional.of(this);
        }

        @Override
        public Range<T>[] union(Range<? extends T> other) {
            throw new UnsupportedOperationException("Waiting to be redesigned");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    var otherStart = other.isNegative() ? other.end() : other.start();
                    if (otherStart.compareTo(end) > 0) {
                        return Optional.of(new BoundedRange<>(end, otherStart));
                    }
                } else if (other.start().compareTo(end) > 0) {
                    return Optional.of(new BoundedRange<>(end, other.start()));

                }
            } else if (other.isBoundedAtEnd()) {
                if (other.end().compareTo(end) > 0) {
                    return Optional.of(new BoundedRange<>(end, other.end()));
                }
            }
            return Optional.empty();
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
    }

    private static final class UnboundedEndRange<T extends Comparable<T>> extends Range<T> {

        private final T start;

        public UnboundedEndRange(T start) {
            this.start = start;
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
            return instant.compareTo(start) >= 0;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    var otherEnd = other.isNegative() ? other.start() : other.end();
                    return otherEnd.compareTo(start) >= 0;
                } else {
                    return other.start().compareTo(start) >= 0;
                }
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
        public boolean isNegative() {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    return intersectionBoundedWithHalfUnbounded(other, start, false);
                } else {
                    if (other.end().compareTo(start) < 0) {
                        return Optional.empty();
                    } else {
                        return Optional.of(new BoundedRange<>(start, other.end()));
                    }
                }
            } else if (other.isBoundedAtStart()) {
                if (other.start().compareTo(start) > 0) {
                    return Optional.of(other);
                }
            }
            return Optional.of(this);
        }

        @Override
        public Range<T>[] union(Range<? extends T> other) {
            throw new UnsupportedOperationException("Waiting to be redesigned");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other.isBoundedAtEnd()) {
                if (other.isBoundedAtStart()) {
                    var otherEnd = other.isNegative() ? other.start() : other.end();
                    if (otherEnd.compareTo(start) < 0) {
                        return Optional.of(new BoundedRange<>(otherEnd, start));
                    }
                } else if (other.end().compareTo(start) < 0) {
                    return Optional.of(new BoundedRange<>(other.end(), start));
                }
            } else if (other.isBoundedAtStart()) {
                if (other.start().compareTo(start) < 0) {
                    return Optional.of(new BoundedRange<>(other.start(), start));
                }
            }
            return Optional.empty();
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
    }

    private static final class BoundedRange<T extends Comparable<T>> extends Range<T> {

        private final T start;
        private final T end;

        public BoundedRange(T start, T end) {
            this.start = start;
            this.end = end;
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
        @SuppressWarnings("unchecked")
        public boolean overlaps(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    T otherStart;
                    T otherEnd;
                    if (other.isNegative()) {
                        otherStart = other.end();
                        otherEnd = other.start();
                    } else {
                        otherStart = other.start();
                        otherEnd = other.end();
                    }
                    return otherStart.compareTo(end) <= 0 && otherEnd.compareTo(start) >= 0;
                } else {
                    return other.start().compareTo(end) <= 0;
                }
            } else if (other.isBoundedAtEnd()) {
                return other.end().compareTo(start) >= 0;
            }
            return true;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isBefore(Range<? extends T> other) {
            return other.isBoundedAtStart() && end.compareTo(other.start()) < 0;
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean isAfter(Range<? extends T> other) {
            return other.isBoundedAtEnd() && start.compareTo(other.end()) > 0;
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
        public boolean isNegative() {
            return start.compareTo(end) > 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    T otherStart;
                    T otherEnd;
                    if (other.isNegative()) {
                        otherStart = other.end();
                        otherEnd = other.start();
                    } else {
                        otherStart = other.start();
                        otherEnd = other.end();
                    }
                    if (otherStart.compareTo(end) > 0 || otherEnd.compareTo(start) < 0) {
                        return Optional.empty();
                    }
                    return Optional.of(new BoundedRange<>(
                        otherStart.compareTo(start) > 0 ? otherStart : start,
                        otherEnd.compareTo(end) < 0 ? otherEnd : end)
                    );
                } else if (other.start().compareTo(end) > 0) {
                    return Optional.empty();
                }
            } else if (other.isBoundedAtEnd()) {
                if (other.end().compareTo(start) < 0) {
                    return Optional.empty();
                }
            }
            return Optional.of(this);
        }

        @Override
        public Range<T>[] union(Range<? extends T> other) {
            throw new UnsupportedOperationException("Waiting to be redesigned");
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other.isBoundedAtStart()) {
                if (other.isBoundedAtEnd()) {
                    T otherStart;
                    T otherEnd;
                    if (other.isNegative()) {
                        otherStart = other.end();
                        otherEnd = other.start();
                    } else {
                        otherStart = other.start();
                        otherEnd = other.end();
                    }
                    if (otherStart.compareTo(end) > 0) {
                        return Optional.of(new BoundedRange<>(end, otherStart));
                    } else if (otherEnd.compareTo(start) < 0) {
                        return Optional.of(new BoundedRange<>(otherEnd, start));
                    }
                } else if (other.start().compareTo(end) > 0) {
                    return Optional.of(new BoundedRange<>(end, other.start()));
                }
            } else if (other.isBoundedAtEnd()) {
                if (other.end().compareTo(start) < 0) {
                    return Optional.of(new BoundedRange<>(other.end(), start));
                }
            }
            return Optional.empty();
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
    }
}
