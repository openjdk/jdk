/*
 *  Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *  Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 */

/*
 * @test
 * @run testng TestMemoryAlignment
 */

import jdk.incubator.foreign.MemoryLayout;

import jdk.incubator.foreign.GroupLayout;
import jdk.incubator.foreign.MemoryLayout.PathElement;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.SequenceLayout;
import jdk.incubator.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.stream.LongStream;

import org.testng.annotations.*;
import static org.testng.Assert.*;

public class TestMemoryAlignment {

    @Test(dataProvider = "alignments")
    public void testAlignedAccess(long align) {
        ValueLayout layout = ValueLayout.JAVA_INT
                .withBitAlignment(32)
                .withOrder(ByteOrder.BIG_ENDIAN);
        assertEquals(layout.bitAlignment(), 32);
        ValueLayout aligned = layout.withBitAlignment(align);
        assertEquals(aligned.bitAlignment(), align); //unreasonable alignment here, to make sure access throws
        VarHandle vh = aligned.varHandle();
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(aligned, scope);
            vh.set(segment, -42);
            int val = (int)vh.get(segment);
            assertEquals(val, -42);
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedAccess(long align) {
        ValueLayout layout = ValueLayout.JAVA_INT
                .withBitAlignment(32)
                .withOrder(ByteOrder.BIG_ENDIAN);
        assertEquals(layout.bitAlignment(), 32);
        ValueLayout aligned = layout.withBitAlignment(align);
        MemoryLayout alignedGroup = MemoryLayout.structLayout(MemoryLayout.paddingLayout(8), aligned);
        assertEquals(alignedGroup.bitAlignment(), align);
        VarHandle vh = aligned.varHandle();
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(alignedGroup, scope);
            vh.set(segment.asSlice(1L), -42);
            assertEquals(align, 8); //this is the only case where access is aligned
        } catch (IllegalArgumentException ex) {
            assertNotEquals(align, 8); //if align != 8, access is always unaligned
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedPath(long align) {
        MemoryLayout layout = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        MemoryLayout aligned = layout.withBitAlignment(align).withName("value");
        GroupLayout alignedGroup = MemoryLayout.structLayout(MemoryLayout.paddingLayout(8), aligned);
        try {
            alignedGroup.varHandle(PathElement.groupElement("value"));
            assertEquals(align, 8); //this is the only case where path is aligned
        } catch (UnsupportedOperationException ex) {
            assertNotEquals(align, 8); //if align != 8, path is always unaligned
        }
    }

    @Test(dataProvider = "alignments")
    public void testUnalignedSequence(long align) {
        SequenceLayout layout = MemoryLayout.sequenceLayout(5, ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN).withBitAlignment(align));
        try {
            VarHandle vh = layout.varHandle(PathElement.sequenceElement());
            try (ResourceScope scope = ResourceScope.newConfinedScope()) {
                MemorySegment segment = MemorySegment.allocateNative(layout, scope);
                for (long i = 0 ; i < 5 ; i++) {
                    vh.set(segment, i, -42);
                }
            }
        } catch (UnsupportedOperationException ex) {
            assertTrue(align > 32); //if align > 32, access is always unaligned (for some elements)
        }
    }

    @Test
    public void testPackedAccess() {
        ValueLayout vChar = ValueLayout.JAVA_BYTE;
        ValueLayout vShort = ValueLayout.JAVA_SHORT.withOrder(ByteOrder.BIG_ENDIAN);
        ValueLayout vInt = ValueLayout.JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);
        //mimic pragma pack(1)
        GroupLayout g = MemoryLayout.structLayout(vChar.withBitAlignment(8).withName("a"),
                               vShort.withBitAlignment(8).withName("b"),
                               vInt.withBitAlignment(8).withName("c"));
        assertEquals(g.bitAlignment(), 8);
        VarHandle vh_c = g.varHandle(PathElement.groupElement("a"));
        VarHandle vh_s = g.varHandle(PathElement.groupElement("b"));
        VarHandle vh_i = g.varHandle(PathElement.groupElement("c"));
        try (ResourceScope scope = ResourceScope.newConfinedScope()) {
            MemorySegment segment = MemorySegment.allocateNative(g, scope);
            vh_c.set(segment, Byte.MIN_VALUE);
            assertEquals(vh_c.get(segment), Byte.MIN_VALUE);
            vh_s.set(segment, Short.MIN_VALUE);
            assertEquals(vh_s.get(segment), Short.MIN_VALUE);
            vh_i.set(segment, Integer.MIN_VALUE);
            assertEquals(vh_i.get(segment), Integer.MIN_VALUE);
        }
    }

    @DataProvider(name = "alignments")
    public Object[][] createAlignments() {
        return LongStream.range(3, 32)
                .mapToObj(v -> new Object[] { 1L << v })
                .toArray(Object[][]::new);
    }
}
