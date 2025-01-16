/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng TestMemoryAlignment
 */

import java.io.File;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestMemoryAlignment {

    @Test(dataProvider = "alignments")
    public void testAlignedAccess(long align) {
        ValueLayout layout = ValueLayout.JAVA_INT
                .withOrder(ByteOrder.BIG_ENDIAN);
        assertEquals(layout.byteAlignment(), 4);
        ValueLayout aligned = layout.withByteAlignment(align);
        assertEquals(aligned.byteAlignment(), align); //unreasonable alignment here, to make sure access throws
        VarHandle vh = aligned.varHandle();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(aligned);;
            vh.set(segment, 0L, -42);
            int val = (int)vh.get(segment, 0L);
            assertEquals(val, -42);
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedAccess(long align) {
        ValueLayout layout = ValueLayout.JAVA_INT
                .withOrder(ByteOrder.BIG_ENDIAN);
        assertEquals(layout.byteAlignment(), 4);
        ValueLayout aligned = layout.withByteAlignment(align);
        try (Arena arena = Arena.ofConfined()) {
            MemoryLayout alignedGroup = MemoryLayout.structLayout(MemoryLayout.paddingLayout(1), aligned);
            assertEquals(alignedGroup.byteAlignment(), align);
            VarHandle vh = aligned.varHandle();
            MemorySegment segment = arena.allocate(alignedGroup);;
            vh.set(segment.asSlice(1L), 0L, -42);
            assertEquals(align, 8); //this is the only case where access is aligned
        } catch (IllegalArgumentException ex) {
            assertNotEquals(align, 8); //if align != 8, access is always unaligned
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedPath(long align) {
        MemoryLayout layout = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        MemoryLayout aligned = layout.withByteAlignment(align).withName("value");
        try {
            GroupLayout alignedGroup = MemoryLayout.structLayout(MemoryLayout.paddingLayout(1), aligned);
            alignedGroup.varHandle(PathElement.groupElement("value"));
            assertEquals(align, 1); //this is the only case where path is aligned
        } catch (IllegalArgumentException ex) {
            assertNotEquals(align, 1); //if align != 8, path is always unaligned
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedSequence(long align) {
        try {
            SequenceLayout layout = MemoryLayout.sequenceLayout(5, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withByteAlignment(align));
            VarHandle vh = layout.varHandle(PathElement.sequenceElement());
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment segment = arena.allocate(layout);;
                for (long i = 0 ; i < 5 ; i++) {
                    vh.set(segment, 0L, i, -42);
                }
            }
        } catch (IllegalArgumentException ex) {
            assertTrue(align > 4); //if align > 4, access is always unaligned (for some elements)
        }
    }

    @Test
    public void testPackedAccess() {
        ValueLayout vChar = ValueLayout.JAVA_BYTE;
        ValueLayout vShort = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN);
        ValueLayout vInt = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        //mimic pragma pack(1)
        GroupLayout g = MemoryLayout.structLayout(vChar.withByteAlignment(1).withName("a"),
                               vShort.withByteAlignment(1).withName("b"),
                               vInt.withByteAlignment(1).withName("c"));
        assertEquals(g.byteAlignment(), 1);
        VarHandle vh_c = g.varHandle(PathElement.groupElement("a"));
        VarHandle vh_s = g.varHandle(PathElement.groupElement("b"));
        VarHandle vh_i = g.varHandle(PathElement.groupElement("c"));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment segment = arena.allocate(g);;
            vh_c.set(segment, 0L, Byte.MIN_VALUE);
            assertEquals(vh_c.get(segment, 0L), Byte.MIN_VALUE);
            vh_s.set(segment, 0L, Short.MIN_VALUE);
            assertEquals(vh_s.get(segment, 0L), Short.MIN_VALUE);
            vh_i.set(segment, 0L, Integer.MIN_VALUE);
            assertEquals(vh_i.get(segment, 0L), Integer.MIN_VALUE);
        }
    }

    @Test(dataProvider = "alignments")
    public void testActualByteAlignment(long align) {
        if (align > (1L << 10)) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            var segment = arena.allocate(4, align);
            assertTrue(segment.maxByteAlignment() >= align);
            // Power of two?
            assertEquals(Long.bitCount(segment.maxByteAlignment()), 1);
            assertEquals(segment.asSlice(1).maxByteAlignment(), 1);
        }
    }

    public void testActualByteAlignmentMappedSegment() throws IOException {
        File tmp = File.createTempFile("tmp", "txt");
        try (FileChannel channel = FileChannel.open(tmp.toPath(), StandardOpenOption.READ, StandardOpenOption.WRITE);
             Arena arena = Arena.ofConfined()) {
            var segment =channel.map(FileChannel.MapMode.READ_WRITE, 0L, 32L, arena);
            // We do not know anything about mapping alignment other than it should
            // be positive.
            assertTrue(segment.maxByteAlignment() >= Byte.BYTES);
            // Power of two?
            assertEquals(Long.bitCount(segment.maxByteAlignment()), 1);
            assertEquals(segment.asSlice(1).maxByteAlignment(), 1);
        } finally {
            tmp.delete();
        }
    }

    @Test()
    public void testActualByteAlignmentNull() {
        long alignment = MemorySegment.NULL.maxByteAlignment();
        assertEquals(1L << 62, alignment);
    }

    @Test(dataProvider = "heapSegments")
    public void testActualByteAlignmentHeap(MemorySegment segment, int bytes) {
        assertEquals(segment.maxByteAlignment(), bytes);
        // A slice at offset 1 should always have an alignment of 1
        var segmentSlice = segment.asSlice(1);
        assertEquals(segmentSlice.maxByteAlignment(), 1);
    }

    @DataProvider(name = "alignments")
    public Object[][] createAlignments() {
        return LongStream.range(1, 20)
                .mapToObj(v -> new Object[] { 1L << v })
                .toArray(Object[][]::new);
    }

    @DataProvider(name = "heapSegments")
    public Object[][] heapSegments() {
        return Stream.of(
                        new Object[]{MemorySegment.ofArray(new byte[]{1}), Byte.BYTES},
                        new Object[]{MemorySegment.ofArray(new short[]{1}), Short.BYTES},
                        new Object[]{MemorySegment.ofArray(new char[]{1}), Character.BYTES},
                        new Object[]{MemorySegment.ofArray(new int[]{1}), Integer.BYTES},
                        new Object[]{MemorySegment.ofArray(new long[]{1}), Long.BYTES},
                        new Object[]{MemorySegment.ofArray(new float[]{1}), Float.BYTES},
                        new Object[]{MemorySegment.ofArray(new double[]{1}), Double.BYTES},
                        new Object[]{MemorySegment.ofBuffer(ByteBuffer.allocate(8)), Byte.BYTES},
                        new Object[]{MemorySegment.ofBuffer(CharBuffer.allocate(8)), Character.BYTES},
                        new Object[]{MemorySegment.ofBuffer(ShortBuffer.allocate(8)), Short.BYTES},
                        new Object[]{MemorySegment.ofBuffer(IntBuffer.allocate(8)), Integer.BYTES},
                        new Object[]{MemorySegment.ofBuffer(LongBuffer.allocate(8)), Long.BYTES},
                        new Object[]{MemorySegment.ofBuffer(FloatBuffer.allocate(8)), Float.BYTES},
                        new Object[]{MemorySegment.ofBuffer(DoubleBuffer.allocate(8)), Double.BYTES}
        )
                .toArray(Object[][]::new);
    }

}
