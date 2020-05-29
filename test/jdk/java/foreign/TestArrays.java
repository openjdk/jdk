/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */

/*
 * @test
 * @run testng TestArrays
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemoryLayouts;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.SequenceLayout;

import java.lang.invoke.VarHandle;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.testng.annotations.*;

import static jdk.incubator.foreign.MemorySegment.READ;
import static org.testng.Assert.*;

public class TestArrays {

    static SequenceLayout bytes = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_BYTE
    );

    static SequenceLayout chars = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_CHAR
    );

    static SequenceLayout shorts = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_SHORT
    );

    static SequenceLayout ints = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_INT
    );

    static SequenceLayout floats = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_FLOAT
    );

    static SequenceLayout longs = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_LONG
    );

    static SequenceLayout doubles = MemoryLayout.ofSequence(100,
            MemoryLayouts.JAVA_DOUBLE
    );

    static VarHandle byteHandle = bytes.varHandle(byte.class, PathElement.sequenceElement());
    static VarHandle charHandle = chars.varHandle(char.class, PathElement.sequenceElement());
    static VarHandle shortHandle = shorts.varHandle(short.class, PathElement.sequenceElement());
    static VarHandle intHandle = ints.varHandle(int.class, PathElement.sequenceElement());
    static VarHandle floatHandle = floats.varHandle(float.class, PathElement.sequenceElement());
    static VarHandle longHandle = longs.varHandle(long.class, PathElement.sequenceElement());
    static VarHandle doubleHandle = doubles.varHandle(double.class, PathElement.sequenceElement());

    static void initBytes(MemoryAddress base, SequenceLayout seq, BiConsumer<MemoryAddress, Long> handleSetter) {
        for (long i = 0; i < seq.elementCount().getAsLong() ; i++) {
            handleSetter.accept(base, i);
        }
    }

    static void checkBytes(MemoryAddress base, SequenceLayout layout) {
        long nBytes = layout.elementCount().getAsLong() * layout.elementLayout().byteSize();
        byte[] arr = base.segment().toByteArray();
        for (long i = 0 ; i < nBytes ; i++) {
            byte expected = (byte)byteHandle.get(base, i);
            byte found = arr[(int)i];
            assertEquals(expected, found);
        }
    }

    @Test(dataProvider = "arrays")
    public void testArrays(Consumer<MemoryAddress> init, SequenceLayout layout) {
        try (MemorySegment segment = MemorySegment.allocateNative(layout)) {
            init.accept(segment.baseAddress());
            checkBytes(segment.baseAddress(), layout);
        }
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class,
                                 IllegalArgumentException.class })
    public void testTooBigForArray() {
        MemorySegment.allocateNative((long) Integer.MAX_VALUE * 2).toByteArray();
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testArrayFromClosedSegment() {
        MemorySegment segment = MemorySegment.allocateNative(8);
        segment.close();
        segment.toByteArray();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testArrayFromHeapSegmentWithoutAccess() {
        MemorySegment segment = MemorySegment.ofArray(new byte[8]);
        segment = segment.withAccessModes(segment.accessModes() & ~READ);
        segment.toByteArray();
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testArrayFromNativeSegmentWithoutAccess() {
        MemorySegment segment = MemorySegment.allocateNative(8);
        segment = segment.withAccessModes(segment.accessModes() & ~READ);
        segment.toByteArray();
    }

    @DataProvider(name = "arrays")
    public Object[][] nativeAccessOps() {
        Consumer<MemoryAddress> byteInitializer =
                (base) -> initBytes(base, bytes, (addr, pos) -> byteHandle.set(addr, pos, (byte)(long)pos));
        Consumer<MemoryAddress> charInitializer =
                (base) -> initBytes(base, chars, (addr, pos) -> charHandle.set(addr, pos, (char)(long)pos));
        Consumer<MemoryAddress> shortInitializer =
                (base) -> initBytes(base, shorts, (addr, pos) -> shortHandle.set(addr, pos, (short)(long)pos));
        Consumer<MemoryAddress> intInitializer =
                (base) -> initBytes(base, ints, (addr, pos) -> intHandle.set(addr, pos, (int)(long)pos));
        Consumer<MemoryAddress> floatInitializer =
                (base) -> initBytes(base, floats, (addr, pos) -> floatHandle.set(addr, pos, (float)(long)pos));
        Consumer<MemoryAddress> longInitializer =
                (base) -> initBytes(base, longs, (addr, pos) -> longHandle.set(addr, pos, (long)pos));
        Consumer<MemoryAddress> doubleInitializer =
                (base) -> initBytes(base, doubles, (addr, pos) -> doubleHandle.set(addr, pos, (double)(long)pos));

        return new Object[][]{
                {byteInitializer, bytes},
                {charInitializer, chars},
                {shortInitializer, shorts},
                {intInitializer, ints},
                {floatInitializer, floats},
                {longInitializer, longs},
                {doubleInitializer, doubles}
        };
    }
}
