package java.util;

/**
 * Utility class containing {@link ContinuousRange} implementations.
 */
class ContinuousRanges {

    private static <T extends Comparable<T>> int endCompareOffset(ContinuousRange.BoundedAtEnd<? extends T> other) {
        return other.isEndExclusive() ? 1 : 0;
    }

    private static <T extends Comparable<T>> int startCompareOffset(ContinuousRange.BoundedAtStart<? extends T> other) {
        return other.isStartExclusive() ? 1 : 0;
    }

    private static <T extends Comparable<T>> boolean startBeforeOrAtEndOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return !startAfterEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endAfterOrAtStartOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return !endBeforeStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startBeforeOrAtStartOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return !startAfterStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endAfterOrAtEndOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return !endBeforeEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endBeforeOrAtStartOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return !endAfterStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startAfterOrAtEndOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return !startBeforeEndOf(target, other);
    }

    private static <T extends Comparable<T>> boolean startAfterOrAtStartOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return !startBeforeStartOf(target, other);
    }

    private static <T extends Comparable<T>> boolean endBeforeOrAtEndOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return !endAfterEndOf(target, other);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endBeforeEndOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return target.end().compareTo(other.end()) <= -(1 ^ endCompareOffset(target) | endCompareOffset(other));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startBeforeStartOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return target.start().compareTo(other.start()) <= -(1 ^ startCompareOffset(target) | startCompareOffset(other));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startAfterStartOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return target.start().compareTo(other.start()) >= (startCompareOffset(target) | (1 ^ startCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endAfterEndOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return target.end().compareTo(other.end()) >= (endCompareOffset(target) | (1 ^ endCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startAfterEndOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return target.start().compareTo(other.end()) >= (1 ^ (startCompareOffset(target) | endCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endBeforeStartOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return target.end().compareTo(other.start()) <= -(1 ^ (endCompareOffset(target) | startCompareOffset(other)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean startBeforeEndOf(ContinuousRange.BoundedAtStart<T> target, ContinuousRange.BoundedAtEnd<? extends T> other) {
        return target.start().compareTo(other.end()) < 0;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> boolean endAfterStartOf(ContinuousRange.BoundedAtEnd<T> target, ContinuousRange.BoundedAtStart<? extends T> other) {
        return target.end().compareTo(other.start()) > 0;
    }

    static final class UnboundedRange<T extends Comparable<T>> implements ContinuousRange<T> {

        @Override
        public boolean contains(T instant) {
            return true;
        }

        @Override
        public boolean overlaps(Range<? extends T> other) {
            return true;
        }

        @Override
        public Range<? extends T> intersection(Range<? extends T> other) {
            return other;
        }

        @Override
        public Range<T> union(Range<? extends T> other) {
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> difference(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                        yield new Union<>(new UnboundedStartRange<>(otherStartBounded.start()), new UnboundedEndRange<>(otherEndBounded.end()));
                    }
                    yield new UnboundedEndRange<>(otherStartBounded.start());
                }
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> new UnboundedStartRange<>(otherEndBounded.end());
                default -> new EmptyRange<>();
            };
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

    static final class UnboundedStartRange<T extends Comparable<T>> implements ContinuousRange.BoundedAtEnd<T> {

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
            if (other instanceof UnboundedRange) {
                return true;
            }
            if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                return endAfterOrAtStartOf(this, otherStartBounded);
            }
            return true;
        }

        @Override
        public boolean isBefore(ContinuousRange.BoundedAtStart<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> intersection(Range<? extends T> other) {
            if (other instanceof UnboundedRange) {
                return this;
            }
            if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherBoundedAtStart) {
                if (endBeforeStartOf(this, otherBoundedAtStart)) {
                    return new EmptyRange<>();
                }
                if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded && endAfterOrAtEndOf(this, otherEndBounded)) {
                    // other is fully contained
                    return other;
                }
                return new BoundedRange<>(otherBoundedAtStart.start(), end);
            } else if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded && endAfterOrAtEndOf(this, otherEndBounded)) {
                return other;
            }
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> union(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                        if (endAfterOrAtEndOf(this, otherEndBounded)) {
                            yield this;
                        }
                        yield new UnboundedStartRange<>(otherEndBounded.end());
                    }
                    if (endAfterOrAtStartOf(this, otherStartBounded)) {
                        yield new UnboundedRange<>();
                    }
                    yield new Union<>(this, (Range<T>) other);
                }
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> {
                    if (endAfterOrAtEndOf(this, otherEndBounded)) {
                        yield this;
                    }
                    yield other;
                }
                // other is unbounded
                default -> other;
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> difference(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (endBeforeStartOf(this, otherStartBounded)) {
                        yield this;
                    }
                    if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded && endAfterEndOf(this, otherEndBounded)) {
                        yield new Union<>(new UnboundedStartRange<>(otherStartBounded.start()), new BoundedRange<>(otherEndBounded.end(), end));
                    }
                    yield new UnboundedStartRange<>(otherStartBounded.start());
                }
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> {
                    if (endBeforeOrAtEndOf(this, otherEndBounded)) {
                        yield new EmptyRange<>();
                    }
                    yield new UnboundedStartRange<>(otherEndBounded.end());
                }
                default -> new EmptyRange<>();
            };
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

    static final class UnboundedEndRange<T extends Comparable<T>> implements ContinuousRange.BoundedAtStart<T> {

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
            if (other instanceof UnboundedRange) {
                return true;
            }
            if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                return startBeforeOrAtEndOf(this, otherEndBounded);
            }
            return true;
        }

        @Override
        public boolean isAfter(ContinuousRange.BoundedAtEnd<? extends T> other) {
            return !overlaps(other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) < 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> intersection(Range<? extends T> other) {
            if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                    if (startAfterEndOf(this, otherEndBounded)) {
                        return new EmptyRange<>();
                    } else if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                        return other;
                    }
                } else if (startAfterEndOf(this, otherEndBounded)) {
                    return new EmptyRange<>();
                }
                return new BoundedRange<>(start, otherEndBounded.end());
            } else if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                    return other;
                }
            }
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> union(Range<? extends T> other) {
            if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                    if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                        if (startAfterOrAtStartOf(this, otherStartBounded)) {
                            return new UnboundedEndRange<>(otherStartBounded.start());
                        }
                        return this;
                    }
                } else if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                    return new UnboundedRange<T>();
                }
            } else if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                return startAfterStartOf(this, otherStartBounded) ? other : this;
            }
            return new Union<>(this, (Range<T>) other);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> difference(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> {
                    if (startAfterEndOf(this, otherEndBounded)) {
                        yield this;
                    }
                    if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded && startBeforeStartOf(this, otherStartBounded)) {
                        yield new Union<>(new BoundedRange<>(start, otherStartBounded.start()), new UnboundedEndRange<>(otherEndBounded.end()));
                    }
                    yield new UnboundedEndRange<>(otherEndBounded.end());
                }
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                        yield new EmptyRange<>();
                    }
                    yield new UnboundedEndRange<>(otherStartBounded.start());
                }
                default -> new EmptyRange<>();
            };
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

    static final class BoundedRange<T extends Comparable<T>> implements ContinuousRange.BoundedAtStart<T>, ContinuousRange.BoundedAtEnd<T> {

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
            if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                    return endAfterOrAtStartOf(this, otherStartBounded) && startBeforeOrAtEndOf(this, otherEndBounded);
                }
                return startBeforeOrAtEndOf(this, otherEndBounded);
            } else if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                return endAfterOrAtStartOf(this, otherStartBounded);
            }
            return true;
        }

        @Override
        public boolean isBefore(T point) {
            return point.compareTo(end) <= 0;
        }

        @Override
        public boolean isBefore(ContinuousRange.BoundedAtStart<? extends T> other) {
            return endBeforeStartOf(this, other);
        }

        @Override
        public boolean isAfter(ContinuousRange.BoundedAtEnd<? extends T> other) {
            return startAfterEndOf(this, other);
        }

        @Override
        public boolean isAfter(T point) {
            return start.compareTo(point) > 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> intersection(Range<? extends T> other) {
            if (other instanceof ContinuousRange.BoundedAtStart<? extends T> otherStartBounded) {
                if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                    if (endBeforeStartOf(this, otherStartBounded) || startAfterEndOf(this, otherEndBounded)) {
                        return new EmptyRange<>();
                    } else if (endAfterOrAtEndOf(this, otherEndBounded)) {
                        if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                            return other;
                        } else if (startBeforeEndOf(this, otherEndBounded)) {
                            return new BoundedRange<>(start, otherEndBounded.end());
                        } else {
                            return new EmptyRange<>();
                        }
                    } else {
                        if (startBeforeOrAtStartOf(this, otherStartBounded)) {
                            return new BoundedRange<>(otherStartBounded.start(), end);
                        }
                        return this;
                    }
                } else if (endBeforeStartOf(this, otherStartBounded)) {
                    return new EmptyRange<>();
                }
            } else if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                if (startAfterEndOf(this, otherEndBounded)) {
                    return new EmptyRange<>();
                }
            }
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> union(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                        if (startAfterEndOf(this, otherEndBounded) || endBeforeStartOf(this, otherStartBounded)) {
                            yield new Union<>(this, (Range<T>) other);
                        }
                        yield new BoundedRange<>(
                            startBeforeOrAtStartOf(this, otherStartBounded) ? start : otherStartBounded.start(),
                            endAfterOrAtEndOf(this, otherEndBounded) ? end : otherEndBounded.end()
                        );
                    } else {
                        if (startAfterOrAtStartOf(this, otherStartBounded)) {
                            yield other;
                        } else if (endAfterOrAtStartOf(this, otherStartBounded)) {
                            yield new UnboundedEndRange<>(otherStartBounded.start());
                        }
                        yield new Union<>(this, (Range<T>) other);
                    }
                }
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> {
                    if (endBeforeOrAtEndOf(this, otherEndBounded)) {
                        yield other;
                    } else if (startBeforeOrAtEndOf(this, otherEndBounded)) {
                        yield new UnboundedStartRange<>(otherEndBounded.end());
                    }
                    yield new Union<>(this, (Range<T>) other);
                }
                default -> other;
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<? extends T> difference(Range<? extends T> other) {
            return switch (other) {
                case ContinuousRange.BoundedAtStart<? extends T> otherStartBounded -> {
                    if (endBeforeStartOf(this, otherStartBounded)) {
                        yield this;
                    }
                    if (other instanceof ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded) {
                        if (startAfterEndOf(this, otherEndBounded)) {
                            yield this;
                        }
                        if (startBeforeStartOf(this, otherStartBounded) && endAfterEndOf(this, otherEndBounded)) {
                            yield new Union<>(new BoundedRange<>(start, otherStartBounded.start()), new BoundedRange<>(otherEndBounded.end(), end));
                        }
                        if (startAfterOrAtStartOf(this, otherStartBounded)) {
                            if (endBeforeOrAtEndOf(this, otherEndBounded)) {
                                yield new EmptyRange<>();
                            }
                            yield new BoundedRange<>(otherEndBounded.end(), end);
                        }
                        if (startAfterOrAtStartOf(this, otherStartBounded)) {
                            yield new EmptyRange<>();
                        }
                        yield new BoundedRange<>(start, otherStartBounded.start());
                    }
                    yield new BoundedRange<>(start, otherStartBounded.start());
                }
                case ContinuousRange.BoundedAtEnd<? extends T> otherEndBounded -> {
                    if (startAfterEndOf(this, otherEndBounded)) {
                        yield this;
                    }
                    if (startBeforeEndOf(this, otherEndBounded)) {
                        yield new BoundedRange<>(start, otherEndBounded.end());
                    }
                    yield new BoundedRange<>(start, otherEndBounded.end());
                }
                default -> new EmptyRange<>();
            };
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
}
