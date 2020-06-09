/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;

import java.lang.invoke.VarHandle;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import org.testng.annotations.*;
import static jdk.incubator.foreign.MemorySegment.*;
import static org.testng.Assert.*;

public class TestSpliterator {

    static final VarHandle INT_HANDLE = MemoryLayout.ofSequence(MemoryLayouts.JAVA_INT)
            .varHandle(int.class, MemoryLayout.PathElement.sequenceElement());

    final static int CARRIER_SIZE = 4;

    @Test(dataProvider = "splits")
    public void testSum(int size, int threshold) {
        SequenceLayout layout = MemoryLayout.ofSequence(size, MemoryLayouts.JAVA_INT);

        //setup
        MemorySegment segment = MemorySegment.allocateNative(layout);
        for (int i = 0; i < layout.elementCount().getAsLong(); i++) {
            INT_HANDLE.set(segment.baseAddress(), (long) i, i);
        }
        long expected = LongStream.range(0, layout.elementCount().getAsLong()).sum();
        //serial
        long serial = sum(0, segment);
        assertEquals(serial, expected);
        //parallel counted completer
        long parallelCounted = new SumSegmentCounted(null, MemorySegment.spliterator(segment, layout), threshold).invoke();
        assertEquals(parallelCounted, expected);
        //parallel recursive action
        long parallelRecursive = new SumSegmentRecursive(MemorySegment.spliterator(segment, layout), threshold).invoke();
        assertEquals(parallelRecursive, expected);
        //parallel stream
        long streamParallel = StreamSupport.stream(MemorySegment.spliterator(segment, layout), true)
                .reduce(0L, TestSpliterator::sumSingle, Long::sum);
        assertEquals(streamParallel, expected);
        segment.close();
    }

    public void testSumSameThread() {
        SequenceLayout layout = MemoryLayout.ofSequence(1024, MemoryLayouts.JAVA_INT);

        //setup
        MemorySegment segment = MemorySegment.allocateNative(layout);
        for (int i = 0; i < layout.elementCount().getAsLong(); i++) {
            INT_HANDLE.set(segment.baseAddress(), (long) i, i);
        }
        long expected = LongStream.range(0, layout.elementCount().getAsLong()).sum();

        //check that a segment w/o ACQUIRE access mode can still be used from same thread
        AtomicLong spliteratorSum = new AtomicLong();
        spliterator(segment.withAccessModes(MemorySegment.READ), layout)
                .forEachRemaining(s -> spliteratorSum.addAndGet(sumSingle(0L, s)));
        assertEquals(spliteratorSum.get(), expected);
    }

    static long sumSingle(long acc, MemorySegment segment) {
        return acc + (int)INT_HANDLE.get(segment.baseAddress(), 0L);
    }

    static long sum(long start, MemorySegment segment) {
        long sum = start;
        MemoryAddress base = segment.baseAddress();
        int length = (int)segment.byteSize();
        for (int i = 0 ; i < length / CARRIER_SIZE ; i++) {
            sum += (int)INT_HANDLE.get(base, (long)i);
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

    @DataProvider(name = "accessScenarios")
    public Object[][] accessScenarios() {
        SequenceLayout layout = MemoryLayout.ofSequence(16, MemoryLayouts.JAVA_INT);
        var mallocSegment = MemorySegment.allocateNative(layout);

        Map<Supplier<Spliterator<MemorySegment>>,Integer> l = Map.of(
            () -> spliterator(mallocSegment.withAccessModes(ALL_ACCESS), layout), ALL_ACCESS,
            () -> spliterator(mallocSegment.withAccessModes(0), layout), 0,
            () -> spliterator(mallocSegment.withAccessModes(READ), layout), READ,
            () -> spliterator(mallocSegment.withAccessModes(CLOSE), layout), 0,
            () -> spliterator(mallocSegment.withAccessModes(READ|WRITE), layout), READ|WRITE,
            () -> spliterator(mallocSegment.withAccessModes(READ|WRITE|ACQUIRE), layout), READ|WRITE|ACQUIRE,
            () -> spliterator(mallocSegment.withAccessModes(READ|WRITE|ACQUIRE|HANDOFF), layout), READ|WRITE|ACQUIRE|HANDOFF

        );
        return l.entrySet().stream().map(e -> new Object[] { e.getKey(), e.getValue() }).toArray(Object[][]::new);
    }

    static Consumer<MemorySegment> assertAccessModes(int accessModes) {
        return segment -> {
            assertTrue(segment.hasAccessModes(accessModes & ~CLOSE));
            assertEquals(segment.accessModes(), accessModes & ~CLOSE);
        };
    }

    @Test(dataProvider = "accessScenarios")
    public void testAccessModes(Supplier<Spliterator<MemorySegment>> spliteratorSupplier,
                                int expectedAccessModes) {
        Spliterator<MemorySegment> spliterator = spliteratorSupplier.get();
        spliterator.forEachRemaining(assertAccessModes(expectedAccessModes));

        spliterator = spliteratorSupplier.get();
        do { } while (spliterator.tryAdvance(assertAccessModes(expectedAccessModes)));

        splitOrConsume(spliteratorSupplier.get(), assertAccessModes(expectedAccessModes));
    }

    static void splitOrConsume(Spliterator<MemorySegment> spliterator,
                               Consumer<MemorySegment> consumer) {
        var s1 = spliterator.trySplit();
        if (s1 != null) {
            splitOrConsume(s1, consumer);
            splitOrConsume(spliterator, consumer);
        } else {
            spliterator.forEachRemaining(consumer);
        }
    }
}
