/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import static jdk.incubator.foreign.ValueLayout.JAVA_BYTE;
import static org.testng.Assert.*;

public class TestSegmentCopy {

    @Test(dataProvider = "slices")
    public void testByteCopy(SegmentSlice s1, SegmentSlice s2) {
        int size = Math.min(s1.byteSize(), s2.byteSize());
        //prepare source and target segments
        for (int i = 0 ; i < size ; i++) {
            Type.BYTE.set(s2, i, 0);
        }
        for (int i = 0 ; i < size ; i++) {
            Type.BYTE.set(s1, i, i);
        }
        //perform copy
        MemorySegment.copy(s1.segment, 0, s2.segment, 0, size);
        //check that copy actually worked
        for (int i = 0 ; i < size ; i++) {
            Type.BYTE.check(s2, i, i);
        }
    }

    @Test(dataProvider = "slices")
    public void testElementCopy(SegmentSlice s1, SegmentSlice s2) {
        if (s1.type.carrier != s2.type.carrier) return;
        int size = Math.min(s1.elementSize(), s2.elementSize());
        //prepare source and target segments
        for (int i = 0 ; i < size ; i++) {
            s2.set(i, 0);
        }
        for (int i = 0 ; i < size ; i++) {
            s1.set(i, i);
        }
        //perform copy
        MemorySegment.copy(s1.segment, s1.type.layout, 0, s2.segment, s2.type.layout, 0, size);
        //check that copy actually worked
        for (int i = 0; i < size; i++) {
            s2.check(i, i);
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedSrc() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(segment, 0, segment, JAVA_BYTE.withBitAlignment(16), 0, 4);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testHyperAlignedDst() {
        MemorySegment segment = MemorySegment.ofArray(new byte[] {1, 2, 3, 4});
        MemorySegment.copy(segment, JAVA_BYTE.withBitAlignment(16), 0, segment, 0, 4);
    }

    enum Type {
        // Byte
        BYTE(byte.class, ValueLayout.JAVA_BYTE, i -> (byte)i),
        //LE
        SHORT_LE(short.class, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (short)i),
        CHAR_LE(char.class, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (char)i),
        INT_LE(int.class, ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN), i -> i),
        FLOAT_LE(float.class, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (float)i),
        LONG_LE(long.class, ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (long)i),
        DOUBLE_LE(double.class, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.LITTLE_ENDIAN), i -> (double)i),
        //BE
        SHORT_BE(short.class, ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN), i -> (short)i),
        CHAR_BE(char.class, ValueLayout.JAVA_CHAR.withOrder(ByteOrder.BIG_ENDIAN), i -> (char)i),
        INT_BE(int.class, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN), i -> i),
        FLOAT_BE(float.class, ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.BIG_ENDIAN), i -> (float)i),
        LONG_BE(long.class, ValueLayout.JAVA_LONG.withOrder(ByteOrder.BIG_ENDIAN), i -> (long)i),
        DOUBLE_BE(double.class, ValueLayout.JAVA_DOUBLE.withOrder(ByteOrder.BIG_ENDIAN), i -> (double)i);

        final ValueLayout layout;
        final IntFunction<Object> valueConverter;
        final Class<?> carrier;

        @SuppressWarnings("unchecked")
        <Z> Type(Class<Z> carrier, ValueLayout layout, IntFunction<Z> valueConverter) {
            this.carrier = carrier;
            this.layout = layout;
            this.valueConverter = (IntFunction<Object>)valueConverter;
        }

        int size() {
            return (int)layout.byteSize();
        }

        VarHandle handle() {
            return MemoryHandles.varHandle(layout);
        }

        void set(SegmentSlice slice, int index, int val) {
            handle().set(slice.segment, index * size(), valueConverter.apply(val));
        }

        void check(SegmentSlice slice, int index, int val) {
            assertEquals(handle().get(slice.segment, index * size()), valueConverter.apply(val));
        }
    }

    static class SegmentSlice {

        enum Kind {
            NATIVE(i -> MemorySegment.allocateNative(i, ResourceScope.newImplicitScope())),
            ARRAY(i -> MemorySegment.ofArray(new byte[i]));

            final IntFunction<MemorySegment> segmentFactory;

            Kind(IntFunction<MemorySegment> segmentFactory) {
                this.segmentFactory = segmentFactory;
            }

            MemorySegment makeSegment(int elems) {
                return segmentFactory.apply(elems);
            }
        }

        final Kind kind;
        final Type type;
        final int first;
        final int last;
        final MemorySegment segment;

        public SegmentSlice(Kind kind, Type type, int first, int last, MemorySegment segment) {
            this.kind = kind;
            this.type = type;
            this.first = first;
            this.last = last;
            this.segment = segment;
        }

        void set(int index, int val) {
            type.set(this, index, val);
        }

        void check(int index, int val) {
            type.check(this, index, val);
        }

        int byteSize() {
            return last - first + 1;
        }

        int elementSize() {
            return byteSize() / type.size();
        }

        @Override
        public String toString() {
            return String.format("SegmentSlice{%s, %d, %d}", type, first, last);
        }
    }

    @DataProvider(name = "slices")
    static Object[][] elementSlices() {
        List<SegmentSlice> slices = new ArrayList<>();
        for (SegmentSlice.Kind kind : SegmentSlice.Kind.values()) {
            MemorySegment segment = kind.makeSegment(16);
            //compute all slices
            for (Type type : Type.values()) {
                for (int index = 0; index < 16; index += type.size()) {
                    MemorySegment first = segment.asSlice(0, index);
                    slices.add(new SegmentSlice(kind, type, 0, index - 1, first));
                    MemorySegment second = segment.asSlice(index);
                    slices.add(new SegmentSlice(kind, type, index, 15, second));
                }
            }
        }
        Object[][] sliceArray = new Object[slices.size() * slices.size()][];
        for (int i = 0 ; i < slices.size() ; i++) {
            for (int j = 0 ; j < slices.size() ; j++) {
                sliceArray[i * slices.size() + j] = new Object[] { slices.get(i), slices.get(j) };
            }
        }
        return sliceArray;
    }
}
