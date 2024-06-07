/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestHeapAlignment
 */

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class TestHeapAlignment {

    @Test(dataProvider = "layouts")
    public void testHeapAlignment(MemorySegment segment, int align, Object val, Object arr, ValueLayout layout, Function<Object, MemorySegment> segmentFactory) {
        assertAligned(align, layout, () -> layout.varHandle().get(segment, 0L));
        assertAligned(align, layout, () -> layout.varHandle().set(segment, 0L, val));
        MemoryLayout seq = MemoryLayout.sequenceLayout(1, layout);
        assertAligned(align, layout, () -> seq.varHandle(MemoryLayout.PathElement.sequenceElement()).get(segment, 0L, 0L));
        assertAligned(align, layout, () -> seq.varHandle(MemoryLayout.PathElement.sequenceElement()).set(segment, 0L, 0L, val));
        assertAligned(align, layout, () -> segment.spliterator(layout));
        if (arr != null) {
            assertAligned(align, layout, () -> MemorySegment.copy(arr, 0, segment, layout, 0, 1));
            assertAligned(align, layout, () -> MemorySegment.copy(segment, layout, 0, arr, 0, 1));
            assertAligned(align, layout, () -> {
                MemorySegment other = segmentFactory.apply(arr);
                MemorySegment.copy(other, layout, 0, segment, layout, 0, 1);
            });
            MemorySegment other = segmentFactory.apply(arr);
            assertAligned(align, layout, () -> {
                MemorySegment.copy(segment, layout, 0, other, layout, 0, 1);
            });
            assertAligned(align, layout, () -> {
                MemorySegment.copy(other, layout, 0, segment, layout, 0, 1);
            });
        }
    }

    static void assertAligned(int align, ValueLayout layout, Runnable runnable) {
        boolean shouldFail = layout.byteAlignment() > align && align != -1;
        try {
            runnable.run();
            if (shouldFail) {
                fail("Should not get here!");
            }
        } catch (IllegalArgumentException ex) {
            if (!shouldFail) {
                fail("Should not get here!");
            } else if (!ex.getMessage().contains("alignment") && !ex.getMessage().contains("Misaligned")) {
                fail("Unexpected exception: " + ex);
            }
        }
    }

    enum SegmentAndAlignment {
        HEAP_BYTE(MemorySegment.ofArray(new byte[8]), 1),
        HEAP_SHORT(MemorySegment.ofArray(new short[4]), 2),
        HEAP_CHAR(MemorySegment.ofArray(new char[4]), 2),
        HEAP_INT(MemorySegment.ofArray(new int[2]), 4),
        HEAP_FLOAT(MemorySegment.ofArray(new float[2]), 4),
        HEAP_LONG(MemorySegment.ofArray(new long[1]), 8),
        HEAP_DOUBLE(MemorySegment.ofArray(new double[1]), 8),
        NATIVE(Arena.ofAuto().allocate(8, 1), -1);

        final MemorySegment segment;
        final int align;

        SegmentAndAlignment(MemorySegment segment, int align) {
            this.segment = segment;
            this.align = align;
        }
    }

    @DataProvider
    public static Object[][] layouts() {
        List<Object[]> layouts = new ArrayList<>();
        for (SegmentAndAlignment testCase : SegmentAndAlignment.values()) {
            layouts.add(new Object[] { testCase.segment, testCase.align, (byte) 42, new byte[]{42}, ValueLayout.JAVA_BYTE, (Function<byte[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, true, null, ValueLayout.JAVA_BOOLEAN, null });
            layouts.add(new Object[] { testCase.segment, testCase.align, (char) 42, new char[]{42}, ValueLayout.JAVA_CHAR, (Function<char[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, (short) 42, new short[]{42}, ValueLayout.JAVA_SHORT, (Function<short[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42, new int[]{42}, ValueLayout.JAVA_INT, (Function<int[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42f, new float[]{42}, ValueLayout.JAVA_FLOAT, (Function<float[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42L, new long[]{42}, ValueLayout.JAVA_LONG, (Function<long[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42d, new double[]{42}, ValueLayout.JAVA_DOUBLE, (Function<double[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, MemorySegment.ofAddress(42), null, ValueLayout.ADDRESS, null });
        }
        return layouts.toArray(new Object[0][]);
    }
}
