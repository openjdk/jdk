/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.nio;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import java.nio.*;
import java.util.concurrent.TimeUnit;
import static java.nio.ByteOrder.*;

/**
 * Benchmark for memory access operations on java.nio.Buffer ( and its views )
 *
 * A large number of variants are covered. The individual benchmarks conform to
 * the following convention:
 *   test(Direct|Heap)(Bulk|Loop)(Get|Put)Char(View)?(Swap)?(RO)?
 *
 * This allows to easily run a subset of particular interest. For example:
 *   Direct only :- "org.openjdk.bench.java.nio.CharBuffers.testDirect.*"
 *   Bulk only   :- "org.openjdk.bench.java.nio.CharBuffers.test.*Bulk.*"
 *   Loop Put Swapped Views: -
 *      test(Direct|Heap)(Loop)(Put)Char(View)+(Swap)+"
 */
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(3)
public class CharBuffers {

    static final int CARRIER_BYTE_WIDTH = 2;

    @Param({"16", "1024", "131072"})
    private int size;

    public char charValue;
    public char[] charArray;
    public CharSequence charSeq;

    public CharBuffer heapCharBuffer;
    public CharBuffer heapCharBufferRO;
    public CharBuffer heapByteBufferAsCharBufferView;
    public CharBuffer heapByteBufferAsCharBufferViewRO;
    public CharBuffer heapByteBufferAsCharBufferViewSwap;
    public CharBuffer heapByteBufferAsCharBufferViewSwapRO;
    public CharBuffer directByteBufferAsCharBufferView;
    public CharBuffer directByteBufferAsCharBufferViewRO;
    public CharBuffer directByteBufferAsCharBufferViewSwap;
    public CharBuffer directByteBufferAsCharBufferViewSwapRO;

    @Setup
    public void setup() {
        charArray = new char[size / CARRIER_BYTE_WIDTH];
        charSeq = "x".repeat(size / CARRIER_BYTE_WIDTH);

        // explicitly allocated heap carrier buffer
        heapCharBuffer = CharBuffer.allocate(size / CARRIER_BYTE_WIDTH);
        heapCharBufferRO = CharBuffer.allocate(size / CARRIER_BYTE_WIDTH).asReadOnlyBuffer();

        // ByteBuffer views
        heapByteBufferAsCharBufferView     = ByteBuffer.allocate(size).order(nativeOrder()).asCharBuffer();
        heapByteBufferAsCharBufferViewRO   = ByteBuffer.allocate(size).order(nativeOrder()).asCharBuffer().asReadOnlyBuffer();
        directByteBufferAsCharBufferView   = ByteBuffer.allocateDirect(size).order(nativeOrder()).asCharBuffer();
        directByteBufferAsCharBufferViewRO = ByteBuffer.allocateDirect(size).order(nativeOrder()).asCharBuffer().asReadOnlyBuffer();

        // endianness swapped
        ByteOrder nonNativeOrder = nativeOrder() == BIG_ENDIAN ? LITTLE_ENDIAN : BIG_ENDIAN;
        heapByteBufferAsCharBufferViewSwap     = ByteBuffer.allocate(size).order(nonNativeOrder).asCharBuffer();
        heapByteBufferAsCharBufferViewSwapRO   = ByteBuffer.allocate(size).order(nonNativeOrder).asCharBuffer().asReadOnlyBuffer();
        directByteBufferAsCharBufferViewSwap   = ByteBuffer.allocateDirect(size).order(nonNativeOrder).asCharBuffer();
        directByteBufferAsCharBufferViewSwapRO = ByteBuffer.allocateDirect(size).order(nonNativeOrder).asCharBuffer().asReadOnlyBuffer();
    }

    // ---------------- HELPER METHODS

    private char innerLoopGetChar(CharBuffer buf) {
        char r = 0;
        for (int i = 0; i < buf.capacity(); i++) {
            r += buf.get(i);
        }
        return r;
    }

    private void innerLoopPutChar(CharBuffer buf) {
        for (int i = 0; i < buf.capacity(); i++) {
            buf.put(i, charValue);
        }
    }

    // -- Heap `wrap(char[] array, int offset, int length)`

    @Benchmark
    public CharBuffer testHeapArrayWrapValidRange() {
        return CharBuffer.wrap(charArray, 1, charArray.length - 1);
    }

    @Benchmark
    public void testHeapArrayWrapBogusRange(Blackhole bh) {
        try {
            bh.consume(CharBuffer.wrap(charArray, -1, charArray.length));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Heap `wrap(CharSequence csq, int start, int end)`

    @Benchmark
    public CharBuffer testHeapCharSeqWrapValidRange() {
        return CharBuffer.wrap(charSeq, 1, charSeq.length() - 1);
    }

    @Benchmark
    public void testHeapCharSeqWrapBogusRange(Blackhole bh) {
        try {
            bh.consume(CharBuffer.wrap(charSeq, -1, charSeq.length()));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Heap___

    @Benchmark
    public char[] testHeapBulkPutChar() {
        heapCharBuffer.put(0, charArray);
        return charArray;
    }

    @Benchmark
    public char[] testHeapBulkGetChar() {
        heapCharBuffer.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public void testHeapLoopPutChar() {
        innerLoopPutChar(heapCharBuffer);
    }

    @Benchmark
    public char testHeapLoopGetChar() {
        return innerLoopGetChar(heapCharBuffer);
    }

    // -- Heap_View_Swap_RO

    @Benchmark
    public char[] testHeapBulkGetCharViewSwapRO() {
        heapByteBufferAsCharBufferViewSwapRO.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public char testHeapLoopGetCharViewSwapRO() {
        return innerLoopGetChar(heapByteBufferAsCharBufferViewSwapRO);
    }

    // -- Heap_View_Swap_RO `subSequence(int start, int end)`

    @Benchmark
    public CharBuffer testHeapSubSeqValidRangeViewSwapRO() {
        return heapByteBufferAsCharBufferViewSwapRO.subSequence(
                1, heapByteBufferAsCharBufferViewSwapRO.length());
    }

    @Benchmark
    public void testHeapSubSeqBogusRangeViewSwapRO(Blackhole bh) {
        try {
            bh.consume(
                    heapByteBufferAsCharBufferViewSwapRO.subSequence(
                        -1, heapByteBufferAsCharBufferViewSwapRO.length()));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Heap_View_Swap_

    @Benchmark
    public char[] testHeapBulkPutCharViewSwap() {
        heapByteBufferAsCharBufferViewSwap.put(0, charArray);
        return charArray;
    }

    @Benchmark
    public char[] testHeapBulkGetCharViewSwap() {
        heapByteBufferAsCharBufferViewSwap.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public void testHeapLoopPutCharViewSwap() {
        innerLoopPutChar(heapByteBufferAsCharBufferViewSwap);
    }

    @Benchmark
    public char testHeapLoopGetCharViewSwap() {
        return innerLoopGetChar(heapByteBufferAsCharBufferViewSwap);
    }

    // -- Heap_View__RO

    @Benchmark
    public char[] testHeapBulkGetCharViewRO() {
        heapByteBufferAsCharBufferViewRO.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public char testHeapLoopGetCharViewRO() {
        return innerLoopGetChar(heapByteBufferAsCharBufferViewRO);
    }

    // -- Heap_View__RO `subSequence(int start, int end)`

    @Benchmark
    public CharBuffer testHeapSubSeqValidRangeViewRO() {
        return heapByteBufferAsCharBufferViewRO.subSequence(
                1, heapByteBufferAsCharBufferViewRO.length());
    }

    @Benchmark
    public void testHeapSubSeqBogusRangeViewRO(Blackhole bh) {
        try {
            bh.consume(
                    heapByteBufferAsCharBufferViewRO.subSequence(
                        -1, heapByteBufferAsCharBufferViewRO.length()));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Heap_View__

    @Benchmark
    public char[] testHeapBulkPutCharView() {
        heapByteBufferAsCharBufferView.put(0, charArray);
        return charArray;
    }

    @Benchmark
    public char[] testHeapBulkGetCharView() {
        heapByteBufferAsCharBufferView.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public void testHeapLoopPutCharView() {
        innerLoopPutChar(heapByteBufferAsCharBufferView);
    }

    @Benchmark
    public char testHeapLoopGetCharView() {
        return innerLoopGetChar(heapByteBufferAsCharBufferView);
    }

    // -- Direct_View_Swap_RO

    @Benchmark
    public char[] testDirectBulkGetCharViewSwapRO() {
        directByteBufferAsCharBufferViewSwapRO.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public char testDirectLoopGetCharViewSwapRO() {
        return innerLoopGetChar(directByteBufferAsCharBufferViewSwapRO);
    }

    // -- Direct_View_Swap_RO `subSequence(int start, int end)`

    @Benchmark
    public CharBuffer testDirectSubSeqValidRangeViewSwapRO() {
        return directByteBufferAsCharBufferViewSwapRO.subSequence(
                1, directByteBufferAsCharBufferViewSwapRO.length());
    }

    @Benchmark
    public void testDirectSubSeqBogusRangeViewSwapRO(Blackhole bh) {
        try {
            bh.consume(
                    directByteBufferAsCharBufferViewSwapRO.subSequence(
                        -1, directByteBufferAsCharBufferViewSwapRO.length()));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Direct_View_Swap_

    @Benchmark
    public char[] testDirectBulkPutCharViewSwap() {
        directByteBufferAsCharBufferViewSwap.put(0, charArray);
        return charArray;
    }

    @Benchmark
    public char[] testDirectBulkGetCharViewSwap() {
        directByteBufferAsCharBufferViewSwap.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public void testDirectLoopPutCharViewSwap() {
        innerLoopPutChar(directByteBufferAsCharBufferViewSwap);
    }

    @Benchmark
    public char testDirectLoopGetCharViewSwap() {
        return innerLoopGetChar(directByteBufferAsCharBufferViewSwap);
    }

    // -- Direct_View__RO

    @Benchmark
    public char[] testDirectBulkGetCharViewRO() {
        directByteBufferAsCharBufferViewRO.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public char testDirectLoopGetCharViewRO() {
        return innerLoopGetChar(directByteBufferAsCharBufferViewRO);
    }

    // -- Direct_View__RO `subSequence(int start, int end)`

    @Benchmark
    public CharBuffer testDirectSubSeqValidRangeViewRO() {
        return directByteBufferAsCharBufferViewRO.subSequence(
                1, directByteBufferAsCharBufferViewRO.length());
    }

    @Benchmark
    public void testDirectSubSeqBogusRangeViewRO(Blackhole bh) {
        try {
            bh.consume(
                    directByteBufferAsCharBufferViewRO.subSequence(
                        -1, directByteBufferAsCharBufferViewRO.length()));
            throw new AssertionError();
        } catch (IndexOutOfBoundsException ioobe) {
            bh.consume(ioobe);
        }
    }

    // -- Direct_View__

    @Benchmark
    public char[] testDirectBulkPutCharView() {
        directByteBufferAsCharBufferView.put(0, charArray);
        return charArray;
    }

    @Benchmark
    public char[] testDirectBulkGetCharView() {
        directByteBufferAsCharBufferView.get(0, charArray);
        return charArray;
    }

    @Benchmark
    public void testDirectLoopPutCharView() {
        innerLoopPutChar(directByteBufferAsCharBufferView);
    }

    @Benchmark
    public char testDirectLoopGetCharView() {
        return innerLoopGetChar(directByteBufferAsCharBufferView);
    }
}
