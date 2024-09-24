package java.util;

/**
 * Utility class containing range implementations.
 */
class Ranges {

    private static <T extends Comparable<T>> int endCompareOffset(Range.BoundedAtEnd<? extends T> other) {
        return other.isEndExclusive() ? 1 : 0;
    }

    private static <T extends Comparable<T>> int startCompareOffset(Range.BoundedAtStart<? extends T> other) {
        return other.isStartExclusive() ? 1 : 0;
    }

    private static <T extends Comparable<T>> boolean startBeforeOrAtEndOf(Range.BoundedAtStart<T> target, Range.BoundedAtEnd<? extends T> other) {
        return !startAfterEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endAfterOrAtStartOf(Range.BoundedAtEnd<T> target, Range.BoundedAtStart<? extends T> other) {
        return !endBeforeStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startBeforeOrAtStartOf(Range.BoundedAtStart<T> target, Range.BoundedAtStart<? extends T> other) {
        return !startAfterStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endAfterOrAtEndOf(Range.BoundedAtEnd<T> target, Range.BoundedAtEnd<? extends T> other) {
        return !endBeforeEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endBeforeOrAtStartOf(Range.BoundedAtEnd<T> target, Range.BoundedAtStart<? extends T> other) {
        return !endAfterStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startAfterOrAtEndOf(Range.BoundedAtStart<T> target, Range.BoundedAtEnd<? extends T> other) {
        return !startBeforeEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startAfterOrAtStartOf(Range.BoundedAtStart<T> target, Range.BoundedAtStart<? extends T> other) {
        return !startBeforeStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endBeforeOrAtEndOf(Range.BoundedAtEnd<T> target, Range.BoundedAtEnd<? extends T> other) {
        return !endAfterEndOf(target, other);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endBeforeEndOf(Range.BoundedAtEnd<T> target, Range.BoundedAtEnd<? extends T> other) {
        return target.end().compareTo(other.end()) <= -(1 ^ endCompareOffset(target) | endCompareOffset(other));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startBeforeStartOf(Range.BoundedAtStart<T> target, Range.BoundedAtStart<? extends T> other) {
        return target.start().compareTo(other.start()) <= -(1 ^ startCompareOffset(target) | startCompareOffset(other));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startAfterStartOf(Range.BoundedAtStart<T> target, Range.BoundedAtStart<? extends T> other) {
        return target.start().compareTo(other.start()) >= (startCompareOffset(target) | (1 ^ startCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endAfterEndOf(Range.BoundedAtEnd<T> target, Range.BoundedAtEnd<? extends T> other) {
        return target.end().compareTo(other.end()) >= (endCompareOffset(target) | (1 ^ endCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startAfterEndOf(Range.BoundedAtStart<T> target, Range.BoundedAtEnd<? extends T> other) {
        return target.start().compareTo(other.end()) >= (1 ^ (startCompareOffset(target) | endCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endBeforeStartOf(Range.BoundedAtEnd<T> target, Range.BoundedAtStart<? extends T> other) {
        return target.end().compareTo(other.start()) <= -(1 ^ (endCompareOffset(target) | startCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startBeforeEndOf(Range.BoundedAtStart<T> target, Range.BoundedAtEnd<? extends T> other) {
        return target.start().compareTo(other.end()) < 0;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endAfterStartOf(Range.BoundedAtEnd<T> target, Range.BoundedAtStart<? extends T> other) {
        return target.end().compareTo(other.start()) > 0;
    }

    static final class UnboundedRange<T extends Comparable<T>> implements Range<T> {

        @Override
        public boolean contains(T instant) {
            return true;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            return true;
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

        public boolean equals(Object obj) {
            return obj instanceof UnboundedRange;
        }

        public int hashCode() {
            return 0;
        }

        @Override
        public String toString() {
            return "-inf - inf";
        }
    }

    static final class UnboundedStartRange<T extends Comparable<T>> implements Range.BoundedAtEnd<T> {

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
        public T end() {
            return end;
        }

        @Override
        public boolean contains(T instant) {
            return instant.compareTo(end) <= -endCompareOffset(this);
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return true;
            }
            if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                return endAfterOrAtStartOf(this, otherStartBounded);
            }
            return true;
        }

        @Override
        public boolean isBefore(Range.BoundedAtStart<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return Optional.of(this);
            }
            if (other instanceof Range.BoundedAtStart<? extends T> otherBoundedAtStart) {
                if (endBeforeStartOf(this, otherBoundedAtStart)) {
                    return Optional.empty();
                }
                if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded && endAfterOrAtEndOf(this, otherEndBounded)) {
                    // other is fully contained
                    return Optional.of(other);
                }
                return Optional.of(new BoundedRange<>(otherBoundedAtStart.start(), end));
            } else if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded && endAfterOrAtEndOf(this, otherEndBounded)) {
                return Optional.of(other);
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return new UnionOfOne<T>(new UnboundedRange<>());
            }
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (endBeforeOrAtEndOf(this, otherEndBounded)) {
                    return new UnionOfOne<>(new UnboundedStartRange<>(otherEndBounded.end()));
                }
            } else if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                if (endAfterOrAtStartOf(this, otherStartBounded)) {
                    return new UnionOfOne<T>(new UnboundedRange<>());
                }
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return Optional.empty();
            }
            if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                var compResult = end.compareTo((T) otherStartBounded.start());
                if (compResult > 0 || (compResult == 0 && excludeEnd && otherStartBounded.isStartExclusive())) {
                    return Optional.of(new BoundedRange<>(end, otherStartBounded.start()));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean isEndExclusive() {
            return excludeEnd;
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
            return "-inf - " + end;
        }
    }

    static final class UnboundedEndRange<T extends Comparable<T>> implements Range.BoundedAtStart<T> {

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
        public boolean contains(T instant) {
            return instant.compareTo(start) >= (startCompareOffset(this));
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return true;
            }
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                return startBeforeOrAtEndOf(this, otherEndBounded);
            }
            return true;
        }

        @Override
        public boolean isAfter(Range.BoundedAtEnd<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) < 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                    if (startAfterEndOf(this, otherEndBounded)) {
                        return Optional.empty();
                    } else if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                        return Optional.of(other);
                    }
                } else if (startAfterEndOf(this, otherEndBounded)) {
                    return Optional.empty();
                }
                return Optional.of(new BoundedRange<>(start, otherEndBounded.end()));
            } else if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                    return Optional.of(other);
                }
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                    if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                        if (startAfterOrAtStartOf(this, otherStartBounded)) {
                            return new UnionOfOne<>(new UnboundedEndRange<>(otherStartBounded.start()));
                        }
                        return new UnionOfOne<>(this);
                    }
                } else if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                    return new UnionOfOne<>(new UnboundedRange<T>());
                }
            } else if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                return startAfterStartOf(this, otherStartBounded) ? new UnionOfOne<>((Range<T>) other) : new UnionOfOne<>(this);
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other instanceof Ranges.UnboundedRange) {
                return Optional.empty();
            }
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                var compResult = start.compareTo((T) otherEndBounded.end());
                if (compResult < 0 || (compResult == 0 && excludeStart && otherEndBounded.isEndExclusive())) {
                    return Optional.of(new BoundedRange<>(otherEndBounded.end(), start));
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean isStartExclusive() {
            return excludeStart;
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

    static final class BoundedRange<T extends Comparable<T>> implements Range.BoundedAtStart<T>, Range.BoundedAtEnd<T> {

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
            if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                    return endAfterOrAtStartOf(this, otherStartBounded) && startBeforeOrAtEndOf(this, otherEndBounded);
                }
                return startBeforeOrAtEndOf(this, otherEndBounded);
            } else if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                return endAfterOrAtStartOf(this, otherStartBounded);
            }
            return true;
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        public boolean isBefore(Range.BoundedAtStart<? extends T> other) {
            return endBeforeStartOf(this, other);
        }

        @Override
        public boolean isAfter(Range.BoundedAtEnd<? extends T> other) {
            return startAfterEndOf(this, other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) > 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<? extends T>> intersection(Range<? extends T> other) {
            if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                    if (endBeforeStartOf(this, otherStartBounded) || startAfterEndOf(this, otherEndBounded)) {
                        return Optional.empty();
                    } else if (endAfterOrAtEndOf(this, otherEndBounded)) {
                        if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                            return Optional.of(other);
                        } else if (startBeforeEndOf(this, otherEndBounded)) {
                            return Optional.of(new BoundedRange<>(start, otherEndBounded.end()));
                        } else {
                            return Optional.empty();
                        }
                    } else {
                        if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                            return Optional.of(new BoundedRange<>(otherStartBounded.start(), end));
                        }
                        return Optional.of(this);
                    }
                } else if (endBeforeStartOf(this, otherStartBounded)) {
                    return Optional.empty();
                }
            } else if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (startAfterEndOf(this, otherEndBounded)) {
                    return Optional.empty();
                }
            }
            return Optional.of(this);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Union<? extends T> union(Range<? extends T> other) {
            if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded) {
                if (other instanceof Range.BoundedAtEnd<? extends T> && endAfterOrAtStartOf(this, otherStartBounded)) {
                    if (otherStartBounded.start().compareTo(start) >= 0) {
                        return new UnionOfOne<>((Range<T>) other);
                    }
                    return new UnionOfOne<>(new BoundedRange<>(otherStartBounded.start(), end));
                } else {
                    if (startAfterOrAtStartOf(this, otherStartBounded)) {
                        return new UnionOfOne<>((Range<T>) other);
                    } else if (otherStartBounded.start().compareTo(end) <= 0) {
                        return new UnionOfOne<>(new UnboundedEndRange<>(otherStartBounded.start()));
                    }
                }
            } else if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded) {
                if (endBeforeOrAtEndOf(this, otherEndBounded)) {
                    return new UnionOfOne<>((Range<T>) other);
                } else if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                    return new UnionOfOne<>(new UnboundedStartRange<>(otherEndBounded.end()));
                }
            }
            return new UnionOfTwo<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Optional<Range<T>> gap(Range<? extends T> other) {
            if (other instanceof Range.BoundedAtStart<? extends T> otherStartBounded && endBeforeStartOf(this, otherStartBounded)) {
                return Optional.of(new BoundedRange<>(end, otherStartBounded.start()));
            } else if (other instanceof Range.BoundedAtEnd<? extends T> otherEndBounded && startAfterEndOf(this, otherEndBounded)) {
                return Optional.of(new BoundedRange<>(otherEndBounded.end(), start));
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

}