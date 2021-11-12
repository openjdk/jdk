/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.IntrinsicCandidate;

import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Utility methods for operating on and creating streams.
 *
 * <p>Unless otherwise stated, streams are created as sequential streams.  A
 * sequential stream can be transformed into a parallel stream by calling the
 * {@code parallel()} method on the created stream.
 *
 * @since 1.8
 */
final class Streams {

    private Streams() {
        throw new Error("no instances");
    }

    /**
     * An {@code int} range spliterator.
     */
    static final class RangeIntSpliterator implements Spliterator.OfInt {
        // Can never be greater that upTo, this avoids overflow if upper bound
        // is Integer.MAX_VALUE
        // All elements are traversed if from == upTo & last == 0
        private int from;
        private final int upTo;
        // 1 if the range is closed and the last element has not been traversed
        // Otherwise, 0 if the range is open, or is a closed range and all
        // elements have been traversed
        private int last;

        RangeIntSpliterator(int from, int upTo, boolean closed) {
            this(from, upTo, closed ? 1 : 0);
        }

        private RangeIntSpliterator(int from, int upTo, int last) {
            this.from = from;
            this.upTo = upTo;
            this.last = last;
        }

        @Override
        public boolean tryAdvance(IntConsumer consumer) {
            Objects.requireNonNull(consumer);

            final int i = from;
            if (i < upTo) {
                from++;
                consumer.accept(i);
                return true;
            }
            else if (last > 0) {
                last = 0;
                consumer.accept(i);
                return true;
            }
            return false;
        }

        @Override
        @IntrinsicCandidate
        public void forEachRemaining(IntConsumer consumer) {
            Objects.requireNonNull(consumer);

            int i = from;
            final int hUpTo = upTo;
            int hLast = last;
            from = upTo;
            last = 0;
            while (i < hUpTo) {
                consumer.accept(i++);
            }
            if (hLast > 0) {
                // Last element of closed range
                consumer.accept(i);
            }
        }

        @Override
        public long estimateSize() {
            // Ensure ranges of size > Integer.MAX_VALUE report the correct size
            return ((long) upTo) - from + last;
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
            long size = estimateSize();
            return size <= 1
                   ? null
                   // Left split always has a half-open range
                   : new RangeIntSpliterator(from, from = from + splitPoint(size), 0);
        }

        /**
         * The spliterator size below which the spliterator will be split
         * at the mid-point to produce balanced splits. Above this size the
         * spliterator will be split at a ratio of
         * 1:(RIGHT_BALANCED_SPLIT_RATIO - 1)
         * to produce right-balanced splits.
         *
         * <p>Such splitting ensures that for very large ranges that the left
         * side of the range will more likely be processed at a lower-depth
         * than a balanced tree at the expense of a higher-depth for the right
         * side of the range.
         *
         * <p>This is optimized for cases such as IntStream.range(0, Integer.MAX_VALUE)
         * that is likely to be augmented with a limit operation that limits the
         * number of elements to a count lower than this threshold.
         */
        private static final int BALANCED_SPLIT_THRESHOLD = 1 << 24;

        /**
         * The split ratio of the left and right split when the spliterator
         * size is above BALANCED_SPLIT_THRESHOLD.
         */
        private static final int RIGHT_BALANCED_SPLIT_RATIO = 1 << 3;

        private int splitPoint(long size) {
            int d = (size < BALANCED_SPLIT_THRESHOLD) ? 2 : RIGHT_BALANCED_SPLIT_RATIO;
            // Cast to int is safe since:
            //   2 <= size < 2^32
            //   2 <= d <= 8
            return (int) (size / d);
        }
    }

    /**
     * A {@code long} range spliterator.
     *
     * This implementation cannot be used for ranges whose size is greater
     * than Long.MAX_VALUE
     */
    static final class RangeLongSpliterator implements Spliterator.OfLong {
        // Can never be greater that upTo, this avoids overflow if upper bound
        // is Long.MAX_VALUE
        // All elements are traversed if from == upTo & last == 0
        private long from;
        private final long upTo;
        // 1 if the range is closed and the last element has not been traversed
        // Otherwise, 0 if the range is open, or is a closed range and all
        // elements have been traversed
        private int last;

        RangeLongSpliterator(long from, long upTo, boolean closed) {
            this(from, upTo, closed ? 1 : 0);
        }

        private RangeLongSpliterator(long from, long upTo, int last) {
            assert upTo - from + last > 0;
            this.from = from;
            this.upTo = upTo;
            this.last = last;
        }

        @Override
        public boolean tryAdvance(LongConsumer consumer) {
            Objects.requireNonNull(consumer);

            final long i = from;
            if (i < upTo) {
                from++;
                consumer.accept(i);
                return true;
            }
            else if (last > 0) {
                last = 0;
                consumer.accept(i);
                return true;
            }
            return false;
        }

        @Override
        public void forEachRemaining(LongConsumer consumer) {
            Objects.requireNonNull(consumer);

            long i = from;
            final long hUpTo = upTo;
            int hLast = last;
            from = upTo;
            last = 0;
            while (i < hUpTo) {
                consumer.accept(i++);
            }
            if (hLast > 0) {
                // Last element of closed range
                consumer.accept(i);
            }
        }

        @Override
        public long estimateSize() {
            return upTo - from + last;
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
            long size = estimateSize();
            return size <= 1
                   ? null
                   // Left split always has a half-open range
                   : new RangeLongSpliterator(from, from = from + splitPoint(size), 0);
        }

        /**
         * The spliterator size below which the spliterator will be split
         * at the mid-point to produce balanced splits. Above this size the
         * spliterator will be split at a ratio of
         * 1:(RIGHT_BALANCED_SPLIT_RATIO - 1)
         * to produce right-balanced splits.
         *
         * <p>Such splitting ensures that for very large ranges that the left
         * side of the range will more likely be processed at a lower-depth
         * than a balanced tree at the expense of a higher-depth for the right
         * side of the range.
         *
         * <p>This is optimized for cases such as LongStream.range(0, Long.MAX_VALUE)
         * that is likely to be augmented with a limit operation that limits the
         * number of elements to a count lower than this threshold.
         */
        private static final long BALANCED_SPLIT_THRESHOLD = 1 << 24;

        /**
         * The split ratio of the left and right split when the spliterator
         * size is above BALANCED_SPLIT_THRESHOLD.
         */
        private static final long RIGHT_BALANCED_SPLIT_RATIO = 1 << 3;

        private long splitPoint(long size) {
            long d = (size < BALANCED_SPLIT_THRESHOLD) ? 2 : RIGHT_BALANCED_SPLIT_RATIO;
            // 2 <= size <= Long.MAX_VALUE
            return size / d;
        }
    }

    private abstract static class AbstractStreamBuilderImpl<T, S extends Spliterator<T>> implements Spliterator<T> {
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
            implements Stream.Builder<T> {
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

        public Stream.Builder<T> add(T t) {
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
                return (c < 2) ? StreamSupport.stream(this, false) : StreamSupport.stream(buffer.spliterator(), false);
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);

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
            Objects.requireNonNull(action);

            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class IntStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Integer, Spliterator.OfInt>
            implements IntStream.Builder, Spliterator.OfInt {
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
                return (c < 2) ? StreamSupport.intStream(this, false) : StreamSupport.intStream(buffer.spliterator(), false);
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(IntConsumer action) {
            Objects.requireNonNull(action);

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
            Objects.requireNonNull(action);

            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class LongStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Long, Spliterator.OfLong>
            implements LongStream.Builder, Spliterator.OfLong {
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
                return (c < 2) ? StreamSupport.longStream(this, false) : StreamSupport.longStream(buffer.spliterator(), false);
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(LongConsumer action) {
            Objects.requireNonNull(action);

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
            Objects.requireNonNull(action);

            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    static final class DoubleStreamBuilderImpl
            extends AbstractStreamBuilderImpl<Double, Spliterator.OfDouble>
            implements DoubleStream.Builder, Spliterator.OfDouble {
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
                return (c < 2) ? StreamSupport.doubleStream(this, false) : StreamSupport.doubleStream(buffer.spliterator(), false);
            }

            throw new IllegalStateException();
        }

        // Spliterator implementation for 0 or 1 element
        // count == -1 for no elements
        // count == -2 for one element held by first

        @Override
        public boolean tryAdvance(DoubleConsumer action) {
            Objects.requireNonNull(action);

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
            Objects.requireNonNull(action);

            if (count == -2) {
                action.accept(first);
                count = -1;
            }
        }
    }

    abstract static class ConcatSpliterator<T, T_SPLITR extends Spliterator<T>>
            implements Spliterator<T> {
        protected final T_SPLITR aSpliterator;
        protected final T_SPLITR bSpliterator;
        // True when no split has occurred, otherwise false
        boolean beforeSplit;
        // Never read after splitting
        final boolean unsized;

        public ConcatSpliterator(T_SPLITR aSpliterator, T_SPLITR bSpliterator) {
            this.aSpliterator = aSpliterator;
            this.bSpliterator = bSpliterator;
            beforeSplit = true;
            // The spliterator is known to be unsized before splitting if the
            // sum of the estimates overflows.
            unsized = aSpliterator.estimateSize() + bSpliterator.estimateSize() < 0;
        }

        @Override
        public T_SPLITR trySplit() {
            @SuppressWarnings("unchecked")
            T_SPLITR ret = beforeSplit ? aSpliterator : (T_SPLITR) bSpliterator.trySplit();
            beforeSplit = false;
            return ret;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> consumer) {
            boolean hasNext;
            if (beforeSplit) {
                hasNext = aSpliterator.tryAdvance(consumer);
                if (!hasNext) {
                    beforeSplit = false;
                    hasNext = bSpliterator.tryAdvance(consumer);
                }
            }
            else
                hasNext = bSpliterator.tryAdvance(consumer);
            return hasNext;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> consumer) {
            if (beforeSplit)
                aSpliterator.forEachRemaining(consumer);
            bSpliterator.forEachRemaining(consumer);
        }

        @Override
        public long estimateSize() {
            if (beforeSplit) {
                // If one or both estimates are Long.MAX_VALUE then the sum
                // will either be Long.MAX_VALUE or overflow to a negative value
                long size = aSpliterator.estimateSize() + bSpliterator.estimateSize();
                return (size >= 0) ? size : Long.MAX_VALUE;
            }
            else {
                return bSpliterator.estimateSize();
            }
        }

        @Override
        public int characteristics() {
            if (beforeSplit) {
                // Concatenation loses DISTINCT and SORTED characteristics
                return aSpliterator.characteristics() & bSpliterator.characteristics()
                       & ~(Spliterator.DISTINCT | Spliterator.SORTED
                           | (unsized ? Spliterator.SIZED | Spliterator.SUBSIZED : 0));
            }
            else {
                return bSpliterator.characteristics();
            }
        }

        @Override
        public Comparator<? super T> getComparator() {
            if (beforeSplit)
                throw new IllegalStateException();
            return bSpliterator.getComparator();
        }

        static class OfRef<T> extends ConcatSpliterator<T, Spliterator<T>> {
            OfRef(Spliterator<T> aSpliterator, Spliterator<T> bSpliterator) {
                super(aSpliterator, bSpliterator);
            }
        }

        private abstract static class OfPrimitive<T, T_CONS, T_SPLITR extends Spliterator.OfPrimitive<T, T_CONS, T_SPLITR>>
                extends ConcatSpliterator<T, T_SPLITR>
                implements Spliterator.OfPrimitive<T, T_CONS, T_SPLITR> {
            private OfPrimitive(T_SPLITR aSpliterator, T_SPLITR bSpliterator) {
                super(aSpliterator, bSpliterator);
            }

            @Override
            public boolean tryAdvance(T_CONS action) {
                boolean hasNext;
                if (beforeSplit) {
                    hasNext = aSpliterator.tryAdvance(action);
                    if (!hasNext) {
                        beforeSplit = false;
                        hasNext = bSpliterator.tryAdvance(action);
                    }
                }
                else
                    hasNext = bSpliterator.tryAdvance(action);
                return hasNext;
            }

            @Override
            public void forEachRemaining(T_CONS action) {
                if (beforeSplit)
                    aSpliterator.forEachRemaining(action);
                bSpliterator.forEachRemaining(action);
            }
        }

        static class OfInt
                extends ConcatSpliterator.OfPrimitive<Integer, IntConsumer, Spliterator.OfInt>
                implements Spliterator.OfInt {
            OfInt(Spliterator.OfInt aSpliterator, Spliterator.OfInt bSpliterator) {
                super(aSpliterator, bSpliterator);
            }
        }

        static class OfLong
                extends ConcatSpliterator.OfPrimitive<Long, LongConsumer, Spliterator.OfLong>
                implements Spliterator.OfLong {
            OfLong(Spliterator.OfLong aSpliterator, Spliterator.OfLong bSpliterator) {
                super(aSpliterator, bSpliterator);
            }
        }

        static class OfDouble
                extends ConcatSpliterator.OfPrimitive<Double, DoubleConsumer, Spliterator.OfDouble>
                implements Spliterator.OfDouble {
            OfDouble(Spliterator.OfDouble aSpliterator, Spliterator.OfDouble bSpliterator) {
                super(aSpliterator, bSpliterator);
            }
        }
    }

    /**
     * Given two Runnables, return a Runnable that executes both in sequence,
     * even if the first throws an exception, and if both throw exceptions, add
     * any exceptions thrown by the second as suppressed exceptions of the first.
     */
    static Runnable composeWithExceptions(Runnable a, Runnable b) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    a.run();
                }
                catch (Throwable e1) {
                    try {
                        b.run();
                    }
                    catch (Throwable e2) {
                        try {
                            e1.addSuppressed(e2);
                        } catch (Throwable ignore) {}
                    }
                    throw e1;
                }
                b.run();
            }
        };
    }

    /**
     * Given two streams, return a Runnable that
     * executes both of their {@link BaseStream#close} methods in sequence,
     * even if the first throws an exception, and if both throw exceptions, add
     * any exceptions thrown by the second as suppressed exceptions of the first.
     */
    static Runnable composedClose(BaseStream<?, ?> a, BaseStream<?, ?> b) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    a.close();
                }
                catch (Throwable e1) {
                    try {
                        b.close();
                    }
                    catch (Throwable e2) {
                        try {
                            e1.addSuppressed(e2);
                        } catch (Throwable ignore) {}
                    }
                    throw e1;
                }
                b.close();
            }
        };
    }

    private static final int DISTINCT = Spliterator.DISTINCT; // 0x1
    private static final int OPERATED_ON = 0x2;
    private static final int SORTED = Spliterator.SORTED; // 0x4
    private static final int CLOSED = 0x8;
    private static final int ORDERED = Spliterator.ORDERED; // 0x10
    private static final int PARALLEL = 0x20;
    private static final int SIZED = Spliterator.SIZED; // 0x40
    private static final int NONNULL = Spliterator.NONNULL; // 0x100
    private static final int IMMUTABLE = Spliterator.IMMUTABLE; // 0x400
    private static final int CONCURRENT = Spliterator.CONCURRENT; // 0x1000
    private static final int SUBSIZED = Spliterator.SUBSIZED; // 0x4000
    private static final int EXTRA_FLAGS = OPERATED_ON | CLOSED | PARALLEL;

    private static sealed class EmptyBaseStream {
        private int state = 0;

        public EmptyBaseStream(EmptyBaseStream input) {
            this.state = input.state & ~(OPERATED_ON);
        }

        public EmptyBaseStream(Spliterator<?> spliterator) {
            this.state = spliterator.characteristics();
        }

        protected final void checkIfOperatedOnOrClosed() {
            if ((state & (OPERATED_ON | CLOSED)) != 0) {
                throw new IllegalStateException(
                        "stream has already been operated upon or closed"
                );
            }
        }

        protected final void checkIfOperatedOnOrClosedAndChangeState() {
            checkIfOperatedOnOrClosed();
            state |= OPERATED_ON;
        }

        protected final void stateDistinct(boolean hasComparator) {
            state |= DISTINCT;
            state &= ~(SIZED | SUBSIZED | CONCURRENT | NONNULL | IMMUTABLE);
            if (hasComparator)
                state &= ~(SORTED);
        }

        protected final void stateDistinctPrimitiveStream() {
            state &= ~(SIZED | SUBSIZED | IMMUTABLE | SORTED);
        }

        protected void stateSorted() {
            state |= SORTED | ORDERED;
            state &= ~(CONCURRENT | NONNULL | IMMUTABLE);
        }

        protected final boolean isSorted() {
            return (state & SORTED) == SORTED;
        }

        protected boolean stateIsUnordered() {
            return (state & ORDERED) == 0;
        }

        protected void stateUnordered() {
            state &= ~ORDERED;
        }

        protected final int stateBareCharacteristics() {
            return state & ~EXTRA_FLAGS;
        }

        public final void close() {
            // nothing to do
            state |= CLOSED;
        }
    }


    /**
     * EmptyStream is an optimization to reduce object allocation
     * during stream creation for empty streams. Most of the
     * methods such as filter() and map() will return "this".
     * We have tried to mirror the behavior of the previous
     * Stream.empty() for spliterator characteristics, parallel()
     * and
     *
     * @param <T>
     */
    static final class EmptyStream<T> extends EmptyBaseStream implements Stream<T> {
        private final Comparator<? super T> comparator;

        public EmptyStream(EmptyBaseStream input) {
            super(input);
            comparator = null;
        }

        public EmptyStream(Spliterator<T> spliterator) {
            super(spliterator);
            comparator = spliterator.hasCharacteristics(Spliterator.SORTED) ?
                    spliterator.getComparator() : null;
        }

        @Override
        public Stream<T> filter(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public IntStream mapToInt(ToIntFunction<? super T> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public LongStream mapToLong(ToLongFunction<? super T> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public <R> Stream<R> mapMulti(BiConsumer<? super T, ? super Consumer<R>> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public IntStream mapMultiToInt(BiConsumer<? super T, ? super IntConsumer> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public LongStream mapMultiToLong(BiConsumer<? super T, ? super LongConsumer> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream mapMultiToDouble(BiConsumer<? super T, ? super DoubleConsumer> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public Stream<T> distinct() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateDistinct(comparator != null);
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> sorted() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateSorted();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> sorted(Comparator<? super T> comparator) {
            // the check is the other way round to normal
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(comparator);
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> peek(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> limit(long maxSize) {
            if (maxSize < 0)
                throw new IllegalArgumentException(Long.toString(maxSize));
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> skip(long n) {
            if (n < 0)
                throw new IllegalArgumentException(Long.toString(n));
            if (n == 0) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> takeWhile(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> dropWhile(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public void forEach(Consumer<? super T> action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        @Override
        public void forEachOrdered(Consumer<? super T> action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        private static final Object[] EMPTY_ARRAY = {};

        @Override
        public Object[] toArray() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ARRAY;
        }

        @Override
        public <A> A[] toArray(IntFunction<A[]> generator) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(generator);
            return Objects.requireNonNull(generator.apply(0));
        }

        @Override
        public T reduce(T identity, BinaryOperator<T> accumulator) {
            Objects.requireNonNull(accumulator);
            checkIfOperatedOnOrClosedAndChangeState();
            return identity;
        }

        @Override
        public Optional<T> reduce(BinaryOperator<T> accumulator) {
            Objects.requireNonNull(accumulator);
            checkIfOperatedOnOrClosedAndChangeState();
            return Optional.empty();
        }

        @Override
        public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
            Objects.requireNonNull(accumulator);
            Objects.requireNonNull(combiner);
            checkIfOperatedOnOrClosedAndChangeState();
            return identity;
        }

        @Override
        public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
            Objects.requireNonNull(supplier);
            Objects.requireNonNull(accumulator);
            Objects.requireNonNull(combiner);
            checkIfOperatedOnOrClosedAndChangeState();
            return supplier.get();
        }

        @Override
        public <R, A> R collect(Collector<? super T, A, R> collector) {
            Objects.requireNonNull(collector);
            checkIfOperatedOnOrClosedAndChangeState();
            return collector.finisher().apply(collector.supplier().get());
        }

        @Override
        public List<T> toList() {
            checkIfOperatedOnOrClosedAndChangeState();
            return List.of();
        }

        @Override
        public Optional<T> min(Comparator<? super T> comparator) {
            Objects.requireNonNull(comparator);
            checkIfOperatedOnOrClosedAndChangeState();
            return Optional.empty();
        }

        @Override
        public Optional<T> max(Comparator<? super T> comparator) {
            Objects.requireNonNull(comparator);
            checkIfOperatedOnOrClosedAndChangeState();
            return Optional.empty();
        }

        @Override
        public long count() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0L;
        }

        @Override
        public boolean anyMatch(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return false;
        }

        @Override
        public boolean allMatch(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public boolean noneMatch(Predicate<? super T> predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public Optional<T> findFirst() {
            checkIfOperatedOnOrClosedAndChangeState();
            return Optional.empty();
        }

        @Override
        public Optional<T> findAny() {
            checkIfOperatedOnOrClosedAndChangeState();
            return Optional.empty();
        }

        @Override
        public Iterator<T> iterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return Collections.emptyIterator();
        }

        @Override
        public Spliterator<T> spliterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            if (isSorted())
                return new EmptySpliterator.OfRefSorted<>(stateBareCharacteristics(), comparator);
            else
                return new EmptySpliterator.OfRef<>(stateBareCharacteristics());
        }


        @Override
        public boolean isParallel() {
            return false;
        }

        @Override
        public Stream<T> sequential() {
            return this;
        }

        @Override
        public Stream<T> parallel() {
            return StreamSupport.stream(spliterator(), true);
        }

        @Override
        public Stream<T> unordered() {
            checkIfOperatedOnOrClosed();
            if (super.stateIsUnordered()) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateUnordered();
            return new EmptyStream<>(this);
        }

        @Override
        public Stream<T> onClose(Runnable closeHandler) {
            return StreamSupport.stream(
                    spliterator(), isParallel()
            ).onClose(closeHandler);
        }
    }

    static final class EmptyIntStream extends EmptyBaseStream implements IntStream {
        public EmptyIntStream(EmptyBaseStream input) {
            super(input);
        }

        public EmptyIntStream(Spliterator.OfInt spliterator) {
            super(spliterator);
        }

        @Override
        public IntStream filter(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream map(IntUnaryOperator mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public LongStream mapToLong(IntToLongFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public IntStream flatMap(IntFunction<? extends IntStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream mapMulti(IntMapMultiConsumer mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream distinct() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateDistinctPrimitiveStream();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream sorted() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateSorted();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream peek(IntConsumer action) {
            Objects.requireNonNull(action);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream limit(long maxSize) {
            if (maxSize < 0)
                throw new IllegalArgumentException(Long.toString(maxSize));
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream skip(long n) {
            if (n < 0)
                throw new IllegalArgumentException(Long.toString(n));
            if (n == 0) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream takeWhile(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream dropWhile(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public void forEach(IntConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        @Override
        public void forEachOrdered(IntConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        private static final int[] EMPTY_ARRAY = {};

        @Override
        public int[] toArray() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ARRAY;
        }

        @Override
        public int reduce(int identity, IntBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return identity;
        }

        @Override
        public OptionalInt reduce(IntBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalInt.empty();
        }

        @Override
        public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner) {
            Objects.requireNonNull(supplier);
            Objects.requireNonNull(accumulator);
            Objects.requireNonNull(combiner);
            checkIfOperatedOnOrClosedAndChangeState();
            return supplier.get();
        }

        @Override
        public OptionalInt min() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt max() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalInt.empty();
        }

        @Override
        public long count() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0L;
        }

        @Override
        public boolean anyMatch(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return false;
        }

        @Override
        public boolean allMatch(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public boolean noneMatch(IntPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public OptionalInt findFirst() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt findAny() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalInt.empty();
        }

        private static final PrimitiveIterator.OfInt EMPTY_ITERATOR =
                new PrimitiveIterator.OfInt() {
                    @Override
                    public int nextInt() {
                        throw new NoSuchElementException();
                    }

                    @Override
                    public boolean hasNext() {
                        return false;
                    }
                };


        @Override
        public PrimitiveIterator.OfInt iterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ITERATOR;
        }

        @Override
        public Spliterator.OfInt spliterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptySpliterator.OfInt(stateBareCharacteristics());
        }

        @Override
        public boolean isParallel() {
            return false;
        }

        @Override
        public IntStream sequential() {
            return this;
        }

        @Override
        public IntStream parallel() {
            return StreamSupport.intStream(spliterator(), true);
        }

        @Override
        public IntStream unordered() {
            if (super.stateIsUnordered()) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateUnordered();
            return new EmptyIntStream(this);
        }

        @Override
        public IntStream onClose(Runnable closeHandler) {
            return StreamSupport.intStream(
                    spliterator(), isParallel()
            ).onClose(closeHandler);
        }

        @Override
        public int sum() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0;
        }

        @Override
        public OptionalDouble average() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public IntSummaryStatistics summaryStatistics() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new IntSummaryStatistics();
        }

        @Override
        public LongStream asLongStream() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream asDoubleStream() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public Stream<Integer> boxed() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }
    }

    static final class EmptyLongStream extends EmptyBaseStream implements LongStream {
        public EmptyLongStream(EmptyBaseStream input) {
            super(input);
        }

        public EmptyLongStream(Spliterator.OfLong spliterator) {
            super(spliterator);
        }

        @Override
        public LongStream filter(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream map(LongUnaryOperator mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public <U> Stream<U> mapToObj(LongFunction<? extends U> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public IntStream mapToInt(LongToIntFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public DoubleStream mapToDouble(LongToDoubleFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public LongStream flatMap(LongFunction<? extends LongStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream mapMulti(LongMapMultiConsumer mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream distinct() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateDistinctPrimitiveStream();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream sorted() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateSorted();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream peek(LongConsumer action) {
            Objects.requireNonNull(action);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream limit(long maxSize) {
            if (maxSize < 0)
                throw new IllegalArgumentException(Long.toString(maxSize));
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream skip(long n) {
            if (n < 0)
                throw new IllegalArgumentException(Long.toString(n));
            if (n == 0) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream takeWhile(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream dropWhile(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public void forEach(LongConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        @Override
        public void forEachOrdered(LongConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        private static final long[] EMPTY_ARRAY = {};

        @Override
        public long[] toArray() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ARRAY;
        }

        @Override
        public long reduce(long identity, LongBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return identity;
        }

        @Override
        public OptionalLong reduce(LongBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalLong.empty();
        }

        @Override
        public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
            Objects.requireNonNull(supplier);
            Objects.requireNonNull(accumulator);
            Objects.requireNonNull(combiner);
            checkIfOperatedOnOrClosedAndChangeState();
            return supplier.get();
        }

        @Override
        public OptionalLong min() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong max() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalLong.empty();
        }

        @Override
        public long count() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0L;
        }

        @Override
        public boolean anyMatch(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return false;
        }

        @Override
        public boolean allMatch(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public boolean noneMatch(LongPredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public OptionalLong findFirst() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalLong.empty();
        }

        @Override
        public OptionalLong findAny() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalLong.empty();
        }

        private static final PrimitiveIterator.OfLong EMPTY_ITERATOR =
                new PrimitiveIterator.OfLong() {
                    @Override
                    public long nextLong() {
                        throw new NoSuchElementException();
                    }

                    @Override
                    public boolean hasNext() {
                        return false;
                    }
                };


        @Override
        public PrimitiveIterator.OfLong iterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ITERATOR;
        }

        @Override
        public Spliterator.OfLong spliterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptySpliterator.OfLong(stateBareCharacteristics());
        }

        @Override
        public boolean isParallel() {
            return false;
        }

        @Override
        public LongStream sequential() {
            return this;
        }

        @Override
        public LongStream parallel() {
            return StreamSupport.longStream(spliterator(), true);
        }

        @Override
        public LongStream unordered() {
            if (super.stateIsUnordered()) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateUnordered();
            return new EmptyLongStream(this);
        }

        @Override
        public LongStream onClose(Runnable closeHandler) {
            return StreamSupport.longStream(
                    spliterator(), isParallel()
            ).onClose(closeHandler);
        }

        @Override
        public long sum() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0;
        }

        @Override
        public OptionalDouble average() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public LongSummaryStatistics summaryStatistics() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new LongSummaryStatistics();
        }

        @Override
        public DoubleStream asDoubleStream() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public Stream<Long> boxed() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }
    }

    static final class EmptyDoubleStream extends EmptyBaseStream implements DoubleStream {
        public EmptyDoubleStream(EmptyBaseStream input) {
            super(input);
        }

        public EmptyDoubleStream(Spliterator.OfDouble spliterator) {
            super(spliterator);
        }

        @Override
        public DoubleStream filter(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream map(DoubleUnaryOperator mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }

        @Override
        public IntStream mapToInt(DoubleToIntFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyIntStream(this);
        }

        @Override
        public LongStream mapToLong(DoubleToLongFunction mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyLongStream(this);
        }

        @Override
        public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream mapMulti(DoubleMapMultiConsumer mapper) {
            Objects.requireNonNull(mapper);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream distinct() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateDistinctPrimitiveStream();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream sorted() {
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateSorted();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream peek(DoubleConsumer action) {
            Objects.requireNonNull(action);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream limit(long maxSize) {
            if (maxSize < 0)
                throw new IllegalArgumentException(Double.toString(maxSize));
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream skip(long n) {
            if (n < 0)
                throw new IllegalArgumentException(Double.toString(n));
            if (n == 0) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream takeWhile(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream dropWhile(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyDoubleStream(this);
        }

        @Override
        public void forEach(DoubleConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        @Override
        public void forEachOrdered(DoubleConsumer action) {
            checkIfOperatedOnOrClosedAndChangeState();
            Objects.requireNonNull(action);
            // do nothing
        }

        private static final double[] EMPTY_ARRAY = {};

        @Override
        public double[] toArray() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ARRAY;
        }

        @Override
        public double reduce(double identity, DoubleBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return identity;
        }

        @Override
        public OptionalDouble reduce(DoubleBinaryOperator op) {
            Objects.requireNonNull(op);
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
            Objects.requireNonNull(supplier);
            Objects.requireNonNull(accumulator);
            Objects.requireNonNull(combiner);
            checkIfOperatedOnOrClosedAndChangeState();
            return supplier.get();
        }

        @Override
        public OptionalDouble min() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public OptionalDouble max() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public long count() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0L;
        }

        @Override
        public boolean anyMatch(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return false;
        }

        @Override
        public boolean allMatch(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public boolean noneMatch(DoublePredicate predicate) {
            Objects.requireNonNull(predicate);
            checkIfOperatedOnOrClosedAndChangeState();
            return true;
        }

        @Override
        public OptionalDouble findFirst() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public OptionalDouble findAny() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        private static final PrimitiveIterator.OfDouble EMPTY_ITERATOR =
                new PrimitiveIterator.OfDouble() {
                    @Override
                    public double nextDouble() {
                        throw new NoSuchElementException();
                    }

                    @Override
                    public boolean hasNext() {
                        return false;
                    }
                };


        @Override
        public PrimitiveIterator.OfDouble iterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return EMPTY_ITERATOR;
        }

        @Override
        public Spliterator.OfDouble spliterator() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptySpliterator.OfDouble(stateBareCharacteristics());
        }

        @Override
        public boolean isParallel() {
            return false;
        }

        @Override
        public DoubleStream sequential() {
            return this;
        }

        @Override
        public DoubleStream parallel() {
            return StreamSupport.doubleStream(spliterator(), true);
        }

        @Override
        public DoubleStream unordered() {
            if (super.stateIsUnordered()) return this;
            checkIfOperatedOnOrClosedAndChangeState();
            super.stateUnordered();
            return new EmptyDoubleStream(this);
        }

        @Override
        public DoubleStream onClose(Runnable closeHandler) {
            return StreamSupport.doubleStream(
                    spliterator(), isParallel()
            ).onClose(closeHandler);
        }

        @Override
        public double sum() {
            checkIfOperatedOnOrClosedAndChangeState();
            return 0;
        }

        @Override
        public OptionalDouble average() {
            checkIfOperatedOnOrClosedAndChangeState();
            return OptionalDouble.empty();
        }

        @Override
        public DoubleSummaryStatistics summaryStatistics() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new DoubleSummaryStatistics();
        }

        @Override
        public Stream<Double> boxed() {
            checkIfOperatedOnOrClosedAndChangeState();
            return new EmptyStream<>(this);
        }
    }

    private abstract static class EmptySpliterator<T, S extends Spliterator<T>, C> {
        private final int characteristics;

        EmptySpliterator(int characteristics) {
            this.characteristics = characteristics;
        }

        public S trySplit() {
            return null;
        }

        public boolean tryAdvance(C consumer) {
            Objects.requireNonNull(consumer);
            return false;
        }

        public void forEachRemaining(C consumer) {
            Objects.requireNonNull(consumer);
        }

        public long estimateSize() {
            return 0;
        }

        public int characteristics() {
            return characteristics;
        }

        private static final class OfRef<T>
                extends EmptySpliterator<T, Spliterator<T>, Consumer<? super T>>
                implements Spliterator<T> {
            OfRef(int characteristics) {
                super(characteristics);
            }
        }

        private static final class OfRefSorted<T>
                extends EmptySpliterator<T, Spliterator<T>, Consumer<? super T>>
                implements Spliterator<T> {
            private final Comparator<? super T> comparator;

            OfRefSorted(int characteristics, Comparator<? super T> comparator) {
                super(characteristics);
                if (!hasCharacteristics(SORTED))
                    throw new IllegalArgumentException("Spliterator only for SORTED");
                this.comparator = comparator;
            }

            @Override
            public Comparator<? super T> getComparator() {
                return comparator;
            }
        }

        private static final class OfInt
                extends EmptySpliterator<Integer, Spliterator.OfInt, IntConsumer>
                implements Spliterator.OfInt {
            OfInt(int characteristics) {
                super(characteristics);
            }
        }

        private static final class OfLong
                extends EmptySpliterator<Long, Spliterator.OfLong, LongConsumer>
                implements Spliterator.OfLong {
            OfLong(int characteristics) {
                super(characteristics);
            }
        }

        private static final class OfDouble
                extends EmptySpliterator<Double, Spliterator.OfDouble, DoubleConsumer>
                implements Spliterator.OfDouble {
            OfDouble(int characteristics) {
                super(characteristics);
            }
        }
    }
}
