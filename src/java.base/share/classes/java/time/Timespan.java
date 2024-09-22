package java.time;

import java.io.Serializable;
import java.time.chrono.ChronoLocalDateTime;
import java.util.Objects;
import java.util.Optional;

public abstract sealed class Timespan implements Serializable, Range<ChronoLocalDateTime<?>> {

    private final boolean isNegative;

    protected Timespan(boolean isNegative) {
        this.isNegative = isNegative;
    }

    protected Timespan() {
        this(false);
    }

    public static Timespan of(ChronoLocalDateTime<?> start, ChronoLocalDateTime<?> end) {
        return new BoundedTimespan(start, end);
    }

    public static Timespan unbounded() {
        return UnboundedTimespan.INSTANCE;
    }

    public static Timespan unboundedEndingAt(ChronoLocalDateTime<?> end) {
        return new UnboundedStartTimespan(end);
    }

    public static Timespan unboundedStartAt(ChronoLocalDateTime<?> start) {
        return new UnboundedEndTimespan(start);
    }

    @Override
    public boolean isNegative() {
        return isNegative;
    }

    private static final class BoundedTimespan extends Timespan {

        private final ChronoLocalDateTime<?> start;
        private final ChronoLocalDateTime<?> end;

        private BoundedTimespan(ChronoLocalDateTime<?> start, ChronoLocalDateTime<?> end) {
            super(start.isAfter(end));
            this.start = start;
            this.end = end;
        }

        @Override
        public ChronoLocalDateTime<?> start() {
            return start;
        }

        @Override
        public ChronoLocalDateTime<?> end() {
            return end;
        }

        @Override
        public boolean contains(ChronoLocalDateTime<?> instant) {
            return !instant.isBefore(premierOfBounds()) && !instant.isAfter(latterOfBounds());
        }

        @Override
        public boolean isBefore(Range<? extends ChronoLocalDateTime<?>> other) {
            if (!other.isBoundedAtStart()) {
                return false;
            }
            if (!other.isBoundedAtEnd()) {
                var otherStart = other.start();
                return end().isBefore(otherStart);
            }
            var otherStart = other.isNegative() ? other.end() : other.start();
            return latterOfBounds().isBefore(otherStart);
        }

        @Override
        public boolean isBefore(ChronoLocalDateTime<?> point) {
            return premierOfBounds().isBefore(point);
        }

        @Override
        public boolean isAfter(Range<? extends ChronoLocalDateTime<?>> other) {
            if (!other.isBoundedAtEnd()) {
                return false;
            }
            if (!other.isBoundedAtStart()) {
                var otherEnd = other.end();
                return start().isAfter(otherEnd);
            }
            var otherEnd = other.isNegative() ? other.start() : other.end();
            return premierOfBounds().isAfter(otherEnd);
        }

        @Override
        public boolean isAfter(ChronoLocalDateTime<?> point) {
            return latterOfBounds().isAfter(point);
        }

        @Override
        public boolean overlaps(Range<? extends ChronoLocalDateTime<?>> other) {
            if (!other.isBoundedAtStart()) {
                if (!other.isBoundedAtEnd()) {
                    return true;
                } else {
                    var otherEnd = other.end();
                    return !otherEnd.isBefore(start());
                }
            } else if (!other.isBoundedAtEnd()) {
                var otherStart = other.start();
                return !otherStart.isAfter(end());
            }

            ChronoLocalDateTime<?> otherStart;
            ChronoLocalDateTime<?> otherEnd;

            if (other.isNegative()) {
                otherStart = other.end();
                otherEnd = other.start();
            } else {
                otherStart = other.start();
                otherEnd = other.end();
            }

            return !otherStart.isAfter(end()) && !otherEnd.isBefore(start());
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
        public Optional<Range<ChronoLocalDateTime<?>>> gap(Range<? extends ChronoLocalDateTime<?>> other) {
            if (other.isBoundedAtStart()) {
                var otherStart = other.start();
                if (otherStart.isAfter(end())) {
                    return Optional.of(Timespan.of(end(), otherStart));
                }
            }
            if (other.isBoundedAtEnd()) {
                var otherEnd = other.end();
                if (otherEnd.isBefore(start())) {
                    return Optional.of(Timespan.of(otherEnd, start()));
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<Range<ChronoLocalDateTime<?>>> intersection(Range<? extends ChronoLocalDateTime<?>> other) {
            if (!other.isBoundedAtStart()) {
                if (!other.isBoundedAtEnd()) {
                    return Optional.of(this);
                } else {
                    var otherEnd = other.end();
                    if (otherEnd.isBefore(start())) {
                        return Optional.empty();
                    }
                    return Optional.of(Timespan.of(start(), otherEnd));
                }
            }

            if (!other.isBoundedAtEnd()) {
                var otherStart = other.start();
                if (otherStart.isAfter(end())) {
                    return Optional.empty();
                }
                return Optional.of(Timespan.of(otherStart, end()));
            }

            var otherStart = other.start();
            var otherEnd = other.end();

            if (otherStart.isAfter(otherEnd)) {
                var tmp = otherStart;
                otherStart = otherEnd;
                otherEnd = tmp;
            }

            if (otherStart.isAfter(end()) || otherEnd.isBefore(start())) {
                return Optional.empty();
            }

            var start = premierOfBounds().isAfter(otherStart) ? premierOfBounds() : otherStart;
            var end = latterOfBounds().isBefore(otherEnd) ? latterOfBounds() : otherEnd;

            return Optional.of(Timespan.of(start, end));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<ChronoLocalDateTime<?>>[] union(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherStart = other.start().query(LocalDateTime::from);
            var otherEnd = other.end().query(LocalDateTime::from);

            if (otherStart.isAfter(end()) || otherEnd.isBefore(start())) {
                return new Range[] {this, other};
            }

            var start = start().isBefore(otherStart) ? start() : other.start();
            var end = end().isAfter(otherEnd) ? end() : other.end();
            return new Range[] {Timespan.of(start, end)};
        }

        private ChronoLocalDateTime<?> latterOfBounds() {
            return isNegative() ? start : end;
        }

        private ChronoLocalDateTime<?> premierOfBounds() {
            return isNegative() ? end : start;
        }

        @Override
        public String toString() {
            return start + " - " + end;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            BoundedTimespan that = (BoundedTimespan) o;
            return Objects.equals(start, that.start) && Objects.equals(end, that.end);
        }

        @Override
        public int hashCode() {
            return Objects.hash(start, end);
        }
    }

    private static final class UnboundedTimespan extends Timespan {

        private static final UnboundedTimespan INSTANCE = new UnboundedTimespan();

        @Override
        public LocalDateTime start() {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalDateTime end() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(ChronoLocalDateTime<?> instant) {
            return true;
        }

        @Override
        public boolean overlaps(Range<? extends ChronoLocalDateTime<?>> other) {
            return true;
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
        public Optional<Range<ChronoLocalDateTime<?>>> intersection(Range<? extends ChronoLocalDateTime<?>> other) {
            return Optional.of(INSTANCE);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<ChronoLocalDateTime<?>>[] union(Range<? extends ChronoLocalDateTime<?>> other) {
            return new Range[] {INSTANCE};
        }

        @Override
        public Optional<Range<ChronoLocalDateTime<?>>> gap(Range<? extends ChronoLocalDateTime<?>> other) {
            return Optional.empty();
        }

        @Override
        public boolean isBefore(Range<? extends ChronoLocalDateTime<?>> other) {
            return false;
        }

        @Override
        public boolean isBefore(ChronoLocalDateTime<?> point) {
            return false;
        }

        @Override
        public boolean isAfter(Range<? extends ChronoLocalDateTime<?>> other) {
            return false;
        }

        @Override
        public boolean isAfter(ChronoLocalDateTime<?> point) {
            return false;
        }

        @Override
        public String toString() {
            return "-inf - +inf";
        }

        public boolean equals(Object o) {
            return o instanceof UnboundedTimespan;
        }

        public int hashCode() {
            return 0;
        }
    }

    private static final class UnboundedStartTimespan extends Timespan {

        private final ChronoLocalDateTime<?> end;

        private UnboundedStartTimespan(ChronoLocalDateTime<?> end) {
            this.end = end;
        }

        @Override
        public ChronoLocalDateTime<?> start() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChronoLocalDateTime<?> end() {
            return end;
        }

        @Override
        public boolean contains(ChronoLocalDateTime<?> instant) {
            return !instant.isAfter(end);
        }

        @Override
        public boolean overlaps(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherStart = other.isNegative() ? other.end() : other.start();
            return !otherStart.isAfter(end);
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
        public Optional<Range<ChronoLocalDateTime<?>>> intersection(Range<? extends ChronoLocalDateTime<?>> other) {
            ChronoLocalDateTime<?> otherStart;
            ChronoLocalDateTime<?> otherEnd;

            if (other.isNegative()) {
                otherStart = other.end();
                otherEnd = other.start();
            } else {
                otherStart = other.start();
                otherEnd = other.end();
            }

            if (otherStart.isAfter(end) || otherEnd.isBefore(end)) {
                return Optional.empty();
            }

            return Optional.of(Timespan.of(end, otherEnd));
        }

        @Override
        public Optional<Range<ChronoLocalDateTime<?>>> gap(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherStart = other.isNegative() ? other.end() : other.start();

            if (otherStart.isAfter(end)) {
                return Optional.of(Timespan.of(end, otherStart));
            }

            return Optional.empty();
        }

        @Override
        public boolean isBefore(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherStart = isNegative() ? other.end() : other.start();
            return otherStart.isAfter(end());
        }

        @Override
        public boolean isBefore(ChronoLocalDateTime<?> point) {
            return end().isBefore(point);
        }

        @Override
        public boolean isAfter(Range<? extends ChronoLocalDateTime<?>> other) {
            return false;
        }

        @Override
        public boolean isAfter(ChronoLocalDateTime<?> point) {
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<ChronoLocalDateTime<?>>[] union(Range<? extends ChronoLocalDateTime<?>> other) {
            ChronoLocalDateTime<?> otherStart;
            ChronoLocalDateTime<?> otherEnd;

            if (other.isNegative()) {
                otherStart = other.end();
                otherEnd = other.start();
            } else {
                otherStart = other.start();
                otherEnd = other.end();
            }

            if (otherStart.isAfter(end)) {
                return new Range[] {this, other};
            }

            var end = end().isAfter(otherEnd) ? end() : otherEnd.query(LocalDateTime::from);

            return new Range[] {new UnboundedStartTimespan(end)};
        }

        @Override
        public String toString() {
            return "-inf - " + end;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof UnboundedStartTimespan unboundedStartTimespan) {
                return end.equals(unboundedStartTimespan.end);
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(end);
        }
    }

    private static final class UnboundedEndTimespan extends Timespan {

        private final ChronoLocalDateTime<?> start;

        private UnboundedEndTimespan(ChronoLocalDateTime<?> start) {
            this.start = start;
        }

        @Override
        public ChronoLocalDateTime<?> start() {
            return start;
        }

        @Override
        public ChronoLocalDateTime<?> end() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean contains(ChronoLocalDateTime<?> instant) {
            return !instant.isBefore(start);
        }

        @Override
        public boolean overlaps(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherEnd = other.isNegative() ? other.start() : other.end();
            return !otherEnd.isBefore(start);
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
        public Optional<Range<ChronoLocalDateTime<?>>> intersection(Range<? extends ChronoLocalDateTime<?>> other) {
            ChronoLocalDateTime<?> otherStart;
            ChronoLocalDateTime<?> otherEnd;

            if (other.isNegative()) {
                otherStart = other.end();
                otherEnd = other.start();
            } else {
                otherStart = other.start();
                otherEnd = other.end();
            }

            if (otherEnd.isBefore(start) || otherStart.isAfter(start)) {
                return Optional.empty();
            }

            return Optional.of(Timespan.of(otherStart, start));
        }

        @Override
        public Optional<Range<ChronoLocalDateTime<?>>> gap(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherEnd = other.isNegative() ? other.start() : other.end();

            if (otherEnd.isBefore(start)) {
                return Optional.of(Timespan.of(otherEnd, start));
            }

            return Optional.empty();
        }

        @Override
        public boolean isBefore(Range<? extends ChronoLocalDateTime<?>> other) {
            return false;
        }

        @Override
        public boolean isBefore(ChronoLocalDateTime<?> point) {
            return false;
        }

        @Override
        public boolean isAfter(Range<? extends ChronoLocalDateTime<?>> other) {
            var otherEnd = other.isNegative() ? other.start() : other.end();
            return otherEnd.isBefore(start());
        }

        @Override
        public boolean isAfter(ChronoLocalDateTime<?> point) {
            return start().isAfter(point);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Range<ChronoLocalDateTime<?>>[] union(Range<? extends ChronoLocalDateTime<?>> other) {
            ChronoLocalDateTime<?> otherStart;
            ChronoLocalDateTime<?> otherEnd;

            if (other.isNegative()) {
                otherStart = other.end();
                otherEnd = other.start();
            } else {
                otherStart = other.start();
                otherEnd = other.end();
            }

            if (otherEnd.isBefore(start)) {
                return new Range[] {this, other};
            }

            var start = start().isBefore(otherStart) ? start() : otherStart.query(LocalDateTime::from);

            return new Range[] {new UnboundedEndTimespan(start)};
        }

        @Override
        public String toString() {
            return start + " - +inf";
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof UnboundedEndTimespan unboundedEndTimespan) {
                return start.equals(unboundedEndTimespan.start);
            }
            return false;
        }

        public int hashCode() {
            return Objects.hash(start);
        }
    }
}
