/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @run testng TestSpliterator
 */

import java.lang.foreign.*;

import java.lang.invoke.VarHandle;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import org.testng.annotations.*;

import static org.testng.Assert.*;

public class TestSpliterator {

    final static int CARRIER_SIZE = 4;

    @Test(dataProvider = "splits")
    public void testSum(int size, int threshold) {
        SequenceLayout layout = MemoryLayout.sequenceLayout(size, ValueLayout.JAVA_INT);

        //setup
        try (Arena arena = Arena.ofShared()) {
            MemorySegment segment = arena.allocate(layout);;
            for (int i = 0; i < layout.elementCount(); i++) {
                segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
            }
            long expected = LongStream.range(0, layout.elementCount()).sum();
            //serial
            long serial = sum(0, segment);
            assertEquals(serial, expected);
            //parallel counted completer
            long parallelCounted = new SumSegmentCounted(null, segment.spliterator(layout.elementLayout()), threshold).invoke();
            assertEquals(parallelCounted, expected);
            //parallel recursive action
            long parallelRecursive = new SumSegmentRecursive(segment.spliterator(layout.elementLayout()), threshold).invoke();
            assertEquals(parallelRecursive, expected);
            //parallel stream
            long streamParallel = segment.elements(layout.elementLayout()).parallel()
                    .reduce(0L, TestSpliterator::sumSingle, Long::sum);
            assertEquals(streamParallel, expected);
        }
    }

    @Test
    public void testSumSameThread() {
        SequenceLayout layout = MemoryLayout.sequenceLayout(1024, ValueLayout.JAVA_INT);

        //setup
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(layout);
        for (int i = 0; i < layout.elementCount(); i++) {
            segment.setAtIndex(ValueLayout.JAVA_INT, i, i);
        }
        long expected = LongStream.range(0, layout.elementCount()).sum();

        //check that a segment w/o ACQUIRE access mode can still be used from same thread
        AtomicLong spliteratorSum = new AtomicLong();
        segment.spliterator(layout.elementLayout())
                .forEachRemaining(s -> spliteratorSum.addAndGet(sumSingle(0L, s)));
        assertEquals(spliteratorSum.get(), expected);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSpliteratorElementSizeTooBig() {
        Arena scope = Arena.ofAuto();
        scope.allocate(2, 1)
                .spliterator(ValueLayout.JAVA_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadStreamElementSizeTooBig() {
        Arena scope = Arena.ofAuto();
        scope.allocate(2, 1)
                .elements(ValueLayout.JAVA_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSpliteratorElementSizeNotMultiple() {
        Arena scope = Arena.ofAuto();
        scope.allocate(7, 1)
                .spliterator(ValueLayout.JAVA_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadStreamElementSizeNotMultiple() {
        Arena scope = Arena.ofAuto();
        scope.allocate(7, 1)
                .elements(ValueLayout.JAVA_INT);
    }

    @Test
    public void testSpliteratorElementSizeMultipleButNotPowerOfTwo() {
        Arena scope = Arena.ofAuto();
        scope.allocate(12, 1)
                .spliterator(ValueLayout.JAVA_INT);
    }

    @Test
    public void testStreamElementSizeMultipleButNotPowerOfTwo() {
        Arena scope = Arena.ofAuto();
        scope.allocate(12, 1)
                .elements(ValueLayout.JAVA_INT);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadSpliteratorElementSizeZero() {
        Arena scope = Arena.ofAuto();
        scope.allocate(7, 1)
                .spliterator(MemoryLayout.sequenceLayout(0, ValueLayout.JAVA_INT));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testBadStreamElementSizeZero() {
        Arena scope = Arena.ofAuto();
        scope.allocate(7, 1)
                .elements(MemoryLayout.sequenceLayout(0, ValueLayout.JAVA_INT));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAligned() {
        Arena scope = Arena.ofAuto();
        MemorySegment segment = scope.allocate(8, 1);
        // compute an alignment constraint (in bytes) which exceed that of the native segment
        long bigByteAlign = Long.lowestOneBit(segment.address()) << 1;
        segment.elements(MemoryLayout.sequenceLayout(2, ValueLayout.JAVA_INT.withByteAlignment(bigByteAlign)));
    }

    static long sumSingle(long acc, MemorySegment segment) {
        return acc + segment.getAtIndex(ValueLayout.JAVA_INT, 0);
    }

    static long sum(long start, MemorySegment segment) {
        long sum = start;
        int length = (int)segment.byteSize();
        for (int i = 0 ; i < length / CARRIER_SIZE ; i++) {
            sum += segment.getAtIndex(ValueLayout.JAVA_INT, i);
        }
        return sum;
    }

    static class SumSegmentCounted extends CountedCompleter<Long> {

        final long threshold;
        long localSum = 0;
        List<SumSegmentCounted> children = new LinkedList<>();

        private Spliterator<MemorySegment> segmentSplitter;

        SumSegmentCounted(SumSegmentCounted parent, Spliterator<MemorySegment> segmentSplitter, long threshold) {
            super(parent);
            this.segmentSplitter = segmentSplitter;
            this.threshold = threshold;
        }

        @Override
        public void compute() {
            Spliterator<MemorySegment> sub;
            while (segmentSplitter.estimateSize() > threshold &&
                    (sub = segmentSplitter.trySplit()) != null) {
                addToPendingCount(1);
                SumSegmentCounted child = new SumSegmentCounted(this, sub, threshold);
                children.add(child);
                child.fork();
            }
            segmentSplitter.forEachRemaining(slice -> {
                localSum += sumSingle(0, slice);
            });
            tryComplete();
        }

        @Override
        public Long getRawResult() {
            long sum = localSum;
            for (SumSegmentCounted c : children) {
                sum += c.getRawResult();
            }
            return sum;
        }
     }

    static class SumSegmentRecursive extends RecursiveTask<Long> {

        final long threshold;
        private final Spliterator<MemorySegment> splitter;
        private long result;

        SumSegmentRecursive(Spliterator<MemorySegment> splitter, long threshold) {
            this.splitter = splitter;
            this.threshold = threshold;
        }

        @Override
        protected Long compute() {
            if (splitter.estimateSize() > threshold) {
                SumSegmentRecursive sub = new SumSegmentRecursive(splitter.trySplit(), threshold);
                sub.fork();
                return compute() + sub.join();
            } else {
                splitter.forEachRemaining(slice -> {
                    result += sumSingle(0, slice);
                });
                return result;
            }
        }
    }

    @DataProvider(name = "splits")
    public Object[][] splits() {
        return new Object[][] {
                { 10, 1 },
                { 100, 1 },
                { 1000, 1 },
                { 10000, 1 },
                { 10, 10 },
                { 100, 10 },
                { 1000, 10 },
                { 10000, 10 },
                { 10, 100 },
                { 100, 100 },
                { 1000, 100 },
                { 10000, 100 },
                { 10, 1000 },
                { 100, 1000 },
                { 1000, 1000 },
                { 10000, 1000 },
                { 10, 10000 },
                { 100, 10000 },
                { 1000, 10000 },
                { 10000, 10000 },
        };
    }
}
