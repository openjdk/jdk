/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestSegmentCopy
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;

import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.testng.Assert.*;

public class TestSegmentCopy {

    static final int TEST_BYTE_SIZE = 16;

    @Test(dataProvider = "segmentKinds")
    public void testByteCopy(SegmentKind kind1, SegmentKind kind2) {
        MemorySegment s1 = kind1.makeSegment(TEST_BYTE_SIZE);
        MemorySegment s2 = kind2.makeSegment(TEST_BYTE_SIZE);

        // for all offsets
        for (int s1Offset = 0; s1Offset < s1.byteSize(); s1Offset++) {
            for (int s2Offset = 0; s2Offset < s2.byteSize(); s2Offset++) {
                long slice1ByteSize = s1.byteSize() - s1Offset;
                long slice2ByteSize = s2.byteSize() - s2Offset;

                long copySize = Math.min(slice1ByteSize, slice2ByteSize);

                //prepare source slice
                for (int i = 0 ; i < copySize; i++) {
                    Type.BYTE.set(s1, s1Offset, i, i);
                }
                //perform copy
                MemorySegment.copy(s1, Type.BYTE.layout, s1Offset, s2, Type.BYTE.layout, s2Offset, copySize);
                //check that copy actually worked
                for (int i = 0; i < copySize; i++) {
                    Type.BYTE.check(s2, s2Offset, i, i);
                }
            }
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class, dataProvider = "segmentKinds")
    public void testReadOnlyCopy(SegmentKind kind1, SegmentKind kind2) {
        MemorySegment s1 = kind1.makeSegment(TEST_BYTE_SIZE);
        MemorySegment s2 = kind2.makeSegment(TEST_BYTE_SIZE);
        // check failure with read-only dest
        MemorySegment.copy(s1, Type.BYTE.layout, 0, s2.asReadOnly(), Type.BYTE.layout, 0, 0);
    }

    @Test(expectedExceptions = IndexOutOfBoundsException.class, dataProvider = "types")
    public void testBadOverflow(Type type) {
        if (type.layout.byteSize() > 1) {
            MemorySegment segment = MemorySegment.ofArray(new byte[100]);
            MemorySegment.copy(segment, type.layout, 0, segment, type.layout, 0, Long.MAX_VALUE);
        } else {
            throw new SkipException("Byte layouts do not overflow");
        }
    }

    @Test(dataProvider = "segmentKindsAndTypes")
    public void testElementCopy(SegmentKind kind1, SegmentKind kind2, Type type1, Type type2) {
        MemorySegment s1 = kind1.makeSegment(TEST_BYTE_SIZE);
        MemorySegment s2 = kind2.makeSegment(TEST_BYTE_SIZE);

        // for all offsets
        for (int s1Offset = 0; s1Offset < s1.byteSize(); s1Offset++) {
            for (int s2Offset = 0; s2Offset < s2.byteSize(); s2Offset++) {
                long slice1ByteSize = s1.byteSize() - s1Offset;
                long slice2ByteSize = s2.byteSize() - s2Offset;

                long slice1ElementSize = slice1ByteSize / type1.size();
                long slice2ElementSize = slice2ByteSize / type2.size();

                long copySize = Math.min(slice1ElementSize, slice2ElementSize);

                //prepare source slice
                for (int i = 0 ; i < copySize; i++) {
                    type1.set(s1, s1Offset, i, i);
                }
                //perform copy
                MemorySegment.copy(s1, type1.layout, s1Offset, s2, type2.layout, s2Offset, copySize);
                //check that copy actually worked
                for (int i = 0; i < copySize; i++) {
                    type2.check(s2, s2Offset, i, i);
                }
            }
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedSrc() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(segment, 0, segment, JAVA_BYTE.withByteAlignment(2), 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedDst() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(segment, JAVA_BYTE.withByteAlignment(2), 0, segment, 0, 4);
    }

    enum Type {
        // Byte
        BYTE(byte.class, JAVA_BYTE, i -> (byte)i),
        //LE
        SHORT_LE(short.class, ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (short)i),
        CHAR_LE(char.class, ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (char)i),
        INT_LE(int.class, ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> i),
        FLOAT_LE(float.class, ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (float)i),
        LONG_LE(long.class, ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (long)i),
        DOUBLE_LE(double.class, ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (double)i),
        //BE
        SHORT_BE(short.class, ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> (short)i),
        CHAR_BE(char.class, ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> (char)i),
        INT_BE(int.class, ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> i),
        FLOAT_BE(float.class, ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> (float)i),
        LONG_BE(long.class, ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> (long)i),
        DOUBLE_BE(double.class, ValueLayout.JAVA_DOUBLE_UNALIGNED.withOrder(ByteOrder.BIG_ENDIAN), i -> (double)i);

        final ValueLayout layout;
        final IntFunction<Object> valueConverter;
        final Class<?> carrier;

        @SuppressWarnings("unchecked")
        <Z> Type(Class<Z> carrier, ValueLayout layout, IntFunction<Z> valueConverter) {
            this.carrier = carrier;
            this.layout = layout;
            this.valueConverter = (IntFunction<Object>)valueConverter;
        }

        long size() {
            return layout.byteSize();
        }

        VarHandle handle() {
            return layout.varHandle();
        }

        void set(MemorySegment segment, long offset, int index, int val) {
            handle().set(segment, offset + (index * size()), valueConverter.apply(val));
        }

        void check(MemorySegment segment, long offset, int index, int val) {
            assertEquals(handle().get(segment, offset + (index * size())), valueConverter.apply(val));
        }
    }

    enum SegmentKind {
        NATIVE(i -> Arena.ofAuto().allocate(i, 1)),
        ARRAY(i -> MemorySegment.ofArray(new byte[i]));

        final IntFunction<MemorySegment> segmentFactory;

        SegmentKind(IntFunction<MemorySegment> segmentFactory) {
            this.segmentFactory = segmentFactory;
        }

        MemorySegment makeSegment(int size) {
            return segmentFactory.apply(size);
        }
    }

    @DataProvider
    static Object[][] segmentKinds() {
        List<Object[]> cases = new ArrayList<>();
        for (SegmentKind kind1 : SegmentKind.values()) {
            for (SegmentKind kind2 : SegmentKind.values()) {
                cases.add(new Object[] {kind1, kind2});
            }
        }
        return cases.toArray(Object[][]::new);
    }

    @DataProvider
    static Object[][] types() {
        return Arrays.stream(Type.values())
                .map(t -> new Object[] { t })
                .toArray(Object[][]::new);
    }

    @DataProvider
    static Object[][] segmentKindsAndTypes() {
        List<Object[]> cases = new ArrayList<>();
        for (Object[] segmentKinds : segmentKinds()) {
            for (Type type1 : Type.values()) {
                for (Type type2 : Type.values()) {
                    if (type1.layout.carrier() == type2.layout.carrier()) {
                        cases.add(new Object[]{segmentKinds[0], segmentKinds[1], type1, type2});
                    }
                }
            }
        }
        return cases.toArray(Object[][]::new);
    }
}
