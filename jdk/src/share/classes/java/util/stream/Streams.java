/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package java.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Utility methods for operating on and creating streams.
 *
 * <p>Unless otherwise stated, streams are created as sequential streams.  A
 * sequential stream can be transformed into a parallel stream by calling the
 * {@code parallel()} method on the created stream.
 *
 * @since 1.8
 */
class Streams {

    private Streams() {
        throw new Error("no instances");
    }

    /**
     * An object instance representing no value, that cannot be an actual
     * data element of a stream.  Used when processing streams that can contain
     * {@code null} elements to distinguish between a {@code null} value and no
     * value.
     */
    static final Object NONE = new Object();

    /**
     * An {@code int} range spliterator.
     */
    static final class RangeIntSpliterator implements Spliterator.OfInt {
        private int from;
        private final int upTo;
        private final int step;

        RangeIntSpliterator(int from, int upTo, int step) {
            this.from = from;
            this.upTo = upTo;
            this.step = step;
        }

        @Override
        public boolean tryAdvance(IntConsumer consumer) {
            boolean hasNext = from < upTo;
            if (hasNext) {
                consumer.accept(from);
                from += step;
            }
            return hasNext;
        }

        @Override
        public void forEachRemaining(IntConsumer consumer) {
            int hUpTo = upTo;
            int hStep = step; // hoist accesses and checks from loop
            for (int i = from; i < hUpTo; i += hStep)
                consumer.accept(i);
            from = upTo;
        }

        @Override
        public long estimateSize() {
            int d = upTo - from;
            return (d / step) + ((d % step == 0) ? 0 : 1);
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED |
                   Spliterator.IMMUTABLE | Spliterator.NONNULL |
                   Spliterator.DISTINCT | Spliterator.SORTED;
        }

        @Override
        public Comparator<? super Integer> getComparator() {
            return null;
        }

        @Override
        public Spliterator.OfInt trySplit() {
            return estimateSize() <= 1
                   ? null
                   : new RangeIntSpliterator(from, from = from + midPoint(), step);
        }

        private int midPoint() {
            // Size is known to be >= 2
            int bisection = (upTo - from) / 2;
            // If bisection > step then round down to nearest multiple of step
            // otherwise round up to step
            return bisection > step ? bisection - bisection % step : step;
        }
    }

    /**
     * A {@code long} range spliterator.
     */
    static final class RangeLongSpliterator implements Spliterator.OfLong {
        private long from;
        private final long upTo;
        private final long step;

        RangeLongSpliterator(long from, long upTo, long step) {
            this.from = from;
            this.upTo = upTo;
            this.step = step;
        }

        @Override
        public boolean tryAdvance(LongConsumer consumer) {
            boolean hasNext = from < upTo;
            if (hasNext) {
                consumer.accept(from);
                from += step;
            }
            return hasNext;
        }

        @Override
        public void forEachRemaining(LongConsumer consumer) {
            long hUpTo = upTo;
            long hStep = step; // hoist accesses and checks from loop
            for (long i = from; i < hUpTo; i += hStep)
                consumer.accept(i);
            from = upTo;
        }

        @Override
        public long estimateSize() {
            long d = upTo - from;
            return (d / step) + ((d % step == 0) ? 0 : 1);
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED |
                   Spliterator.IMMUTABLE | Spliterator.NONNULL |
                   Spliterator.DISTINCT | Spliterator.SORTED;
        }

        @Override
        public Comparator<? super Long> getComparator() {
            return null;
        }

        @Override
        public Spliterator.OfLong trySplit() {
            return estimateSize() <= 1
                   ? null
                   : new RangeLongSpliterator(from, from = from + midPoint(), step);
        }

        private long midPoint() {
            // Size is known to be >= 2
            long bisection = (upTo - from) / 2;
            // If bisection > step then round down to nearest multiple of step
            // otherwise round up to step
            return bisection > step ? bisection - bisection % step : step;
        }
    }

    /**
     * A {@code double} range spliterator.
     *
     * <p>The traversing and splitting logic is equivalent to that of
     * {@code RangeLongSpliterator} for increasing values with a {@code step} of
     * {@code 1}.
     *
     *  <p>A {@code double} value is calculated from the function
     * {@code start + i * step} where {@code i} is the absolute position of the
     * value when traversing an instance of this class that has not been split.
     * This ensures the same values are produced at the same absolute positions
     * regardless of how an instance of this class is split or traversed.
     */
    static final class RangeDoubleSpliterator implements Spliterator.OfDouble {
        private final double from;
        private final double upTo;
        private final double step;

        private long lFrom;
        private final long lUpTo;

        RangeDoubleSpliterator(double from, double upTo, double step, long lFrom, long lUpTo) {
            this.from = from;
            this.upTo = upTo;
            this.step = step;
            this.lFrom = lFrom;
            this.lUpTo = lUpTo;
        }

        @Override
        public boolean tryAdvance(DoubleConsumer consumer) {
            boolean hasNext = lFrom < lUpTo;
            if (hasNext) {
                consumer.accept(from + lFrom * step);
                lFrom++;
            }
            return hasNext;
        }

        @Override
        public void forEachRemaining(DoubleConsumer consumer) {
            double hOrigin = from;
            double hStep = step;
            long hLUpTo = lUpTo;
            long i = lFrom;
            for (; i < hLUpTo; i++) {
                consumer.accept(hOrigin + i * hStep);
            }
            lFrom = i;
        }

        @Override
        public long estimateSize() {
            return lUpTo - lFrom;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED |
                   Spliterator.IMMUTABLE | Spliterator.NONNULL |
                   Spliterator.DISTINCT | Spliterator.SORTED;
        }

        @Override
        public Comparator<? super Double> getComparator() {
            return null;
        }

        @Override
        public Spliterator.OfDouble trySplit() {
            return estimateSize() <= 1
                   ? null
                   : new RangeDoubleSpliterator(from, upTo, step, lFrom, lFrom = lFrom + midPoint());
        }

        private long midPoint() {
            // Size is known to be >= 2
            return (lUpTo - lFrom) / 2;
        }
    }

    private static abstract class AbstractStreamBuilderImpl<T, S extends Spliterator<T>> implements Spliterator<T> {
        // >= 0 when building, < 0 when built
        // -1 == no elements
        // -2 == one element, held by first
        // -3 == two or more elements, held by buffer
        int count;

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public S trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return -count - 1;
        }

        @Override
        public int characteristics() {
            return Spliterator.SIZED | Spliterator.SUBSIZED |
                   Spliterator.ORDERED | Spliterator.IMMUTABLE;
        }
    }

    static final class StreamBuilderImpl<T>
            extends AbstractStreamBuilderImpl<T, Spliterator<T>>
            implements StreamBuilder<T> {
        // The first element in the stream
        // valid if count == 1
        T first;

        // The first and subsequent elements in the stream
        // non-null if count == 2
        SpinedBuffer<T> buffer;

        /**
         * Constructor for building a stream of 0 or more elements.
         */
        StreamBuilderImpl() { }

        /**
         * Constructor for a singleton stream.
         *
         * @param t the single element
         */
        StreamBuilderImpl(T t) {
            first = t;
            count = -2;
        }

        // StreamBuilder implementation

        @Override
        public void accept(T t) {
            if (count == 0) {
                first = t;
                count++;
            }
            else if (count > 0) {
                if (buffer == null) {
                    buffer = new SpinedBuffer<>();
                    buffer.accept(first);
                    count++;
                }

                buffer.accept(t);
            }
            else {
                throw new IllegalStateException();
            }
        }

        public StreamBuilder<T> add(T t) {
            accept(t);
            return this;
        }

        @Override
        public Stream<T> build() {
            int c = count;
            if (c >= 0) {
                // Switch count to negative value signalling the builder is built
                count = -count - 1;
                // Use this spliterator if 0 or 1 elements, otherwise use
                // the spliterator of the spined buffer
                return (c < 2) ? StreamSupport.stream(this) : StreamSupport.stream(buffer.spliterator());
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
                return true;
            }
            else {
                return false;
            }
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class IntStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Integer, Spliterator.OfInt>
            implements StreamBuilder.OfInt, Spliterator.OfInt {
        // The first element in the stream
        // valid if count == 1
        int first;

        // The first and subsequent elements in the stream
        // non-null if count == 2
        SpinedBuffer.OfInt buffer;

        /**
         * Constructor for building a stream of 0 or more elements.
         */
        IntStreamBuilderImpl() { }

        /**
         * Constructor for a singleton stream.
         *
         * @param t the single element
         */
        IntStreamBuilderImpl(int t) {
            first = t;
            count = -2;
        }

        // StreamBuilder implementation

        @Override
        public void accept(int t) {
            if (count == 0) {
                first = t;
                count++;
            }
            else if (count > 0) {
                if (buffer == null) {
                    buffer = new SpinedBuffer.OfInt();
                    buffer.accept(first);
                    count++;
                }

                buffer.accept(t);
            }
            else {
                throw new IllegalStateException();
            }
        }

        @Override
        public IntStream build() {
            int c = count;
            if (c >= 0) {
                // Switch count to negative value signalling the builder is built
                count = -count - 1;
                // Use this spliterator if 0 or 1 elements, otherwise use
                // the spliterator of the spined buffer
                return (c < 2) ? StreamSupport.intStream(this) : StreamSupport.intStream(buffer.spliterator());
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
                return true;
            }
            else {
                return false;
            }
        }

        @Override
        public void forEachRemaining(IntConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class LongStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Long, Spliterator.OfLong>
            implements StreamBuilder.OfLong, Spliterator.OfLong {
        // The first element in the stream
        // valid if count == 1
        long first;

        // The first and subsequent elements in the stream
        // non-null if count == 2
        SpinedBuffer.OfLong buffer;

        /**
         * Constructor for building a stream of 0 or more elements.
         */
        LongStreamBuilderImpl() { }

        /**
         * Constructor for a singleton stream.
         *
         * @param t the single element
         */
        LongStreamBuilderImpl(long t) {
            first = t;
            count = -2;
        }

        // StreamBuilder implementation

        @Override
        public void accept(long t) {
            if (count == 0) {
                first = t;
                count++;
            }
            else if (count > 0) {
                if (buffer == null) {
                    buffer = new SpinedBuffer.OfLong();
                    buffer.accept(first);
                    count++;
                }

                buffer.accept(t);
            }
            else {
                throw new IllegalStateException();
            }
        }

        @Override
        public LongStream build() {
            int c = count;
            if (c >= 0) {
                // Switch count to negative value signalling the builder is built
                count = -count - 1;
                // Use this spliterator if 0 or 1 elements, otherwise use
                // the spliterator of the spined buffer
                return (c < 2) ? StreamSupport.longStream(this) : StreamSupport.longStream(buffer.spliterator());
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(LongConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
                return true;
            }
            else {
                return false;
            }
        }

        @Override
        public void forEachRemaining(LongConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class DoubleStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Double, Spliterator.OfDouble>
            implements StreamBuilder.OfDouble, Spliterator.OfDouble {
        // The first element in the stream
        // valid if count == 1
        double first;

        // The first and subsequent elements in the stream
        // non-null if count == 2
        SpinedBuffer.OfDouble buffer;

        /**
         * Constructor for building a stream of 0 or more elements.
         */
        DoubleStreamBuilderImpl() { }

        /**
         * Constructor for a singleton stream.
         *
         * @param t the single element
         */
        DoubleStreamBuilderImpl(double t) {
            first = t;
            count = -2;
        }

        // StreamBuilder implementation

        @Override
        public void accept(double t) {
            if (count == 0) {
                first = t;
                count++;
            }
            else if (count > 0) {
                if (buffer == null) {
                    buffer = new SpinedBuffer.OfDouble();
                    buffer.accept(first);
                    count++;
                }

                buffer.accept(t);
            }
            else {
                throw new IllegalStateException();
            }
        }

        @Override
        public DoubleStream build() {
            int c = count;
            if (c >= 0) {
                // Switch count to negative value signalling the builder is built
                count = -count - 1;
                // Use this spliterator if 0 or 1 elements, otherwise use
                // the spliterator of the spined buffer
                return (c < 2) ? StreamSupport.doubleStream(this) : StreamSupport.doubleStream(buffer.spliterator());
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(DoubleConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
                return true;
            }
            else {
                return false;
            }
        }

        @Override
        public void forEachRemaining(DoubleConsumer action) {
            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }
}
