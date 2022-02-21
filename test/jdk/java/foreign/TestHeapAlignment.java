/*
 *  Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestHeapAlignment
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;
import jdk.incubator.foreign.ValueLayout;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.testng.Assert.fail;

public class TestHeapAlignment {

    @Test(dataProvider = "layouts")
    public void testHeapAlignment(MemorySegment segment, int align, Object val, Object arr, ValueLayout layout, Function<Object, MemorySegment> segmentFactory) {
        assertAligned(align, layout, () -> layout.varHandle().get(segment));
        assertAligned(align, layout, () -> layout.varHandle().set(segment, val));
        MemoryLayout seq = MemoryLayout.sequenceLayout(10, layout);
        assertAligned(align, layout, () -> seq.varHandle(MemoryLayout.PathElement.sequenceElement()).get(segment, 0L));
        assertAligned(align, layout, () -> seq.varHandle(MemoryLayout.PathElement.sequenceElement()).set(segment, 0L, val));
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

    static final ValueLayout.OfChar JAVA_CHAR_ALIGNED = ValueLayout.JAVA_CHAR.withBitAlignment(16);
    static final ValueLayout.OfShort JAVA_SHORT_ALIGNED = ValueLayout.JAVA_SHORT.withBitAlignment(16);
    static final ValueLayout.OfInt JAVA_INT_ALIGNED = ValueLayout.JAVA_INT.withBitAlignment(32);
    static final ValueLayout.OfFloat JAVA_FLOAT_ALIGNED = ValueLayout.JAVA_FLOAT.withBitAlignment(32);
    static final ValueLayout.OfLong JAVA_LONG_ALIGNED = ValueLayout.JAVA_LONG.withBitAlignment(64);
    static final ValueLayout.OfDouble JAVA_DOUBLE_ALIGNED = ValueLayout.JAVA_DOUBLE.withBitAlignment(64);
    static final ValueLayout.OfAddress ADDRESS_ALIGNED = ValueLayout.ADDRESS.withBitAlignment(ValueLayout.ADDRESS.bitSize());

    enum SegmentAndAlignment {
        HEAP_BYTE(MemorySegment.ofArray(new byte[8]), 1),
        HEAP_SHORT(MemorySegment.ofArray(new short[4]), 2),
        HEAP_CHAR(MemorySegment.ofArray(new char[4]), 2),
        HEAP_INT(MemorySegment.ofArray(new int[2]), 4),
        HEAP_FLOAT(MemorySegment.ofArray(new float[2]), 4),
        HEAP_LONG(MemorySegment.ofArray(new long[1]), 8),
        HEAP_DOUBLE(MemorySegment.ofArray(new double[1]), 8),
        NATIVE(MemorySegment.allocateNative(8, ResourceScope.newImplicitScope()), -1);

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
            layouts.add(new Object[] { testCase.segment, testCase.align, (char) 42, new char[]{42}, JAVA_CHAR_ALIGNED, (Function<char[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, (short) 42, new short[]{42}, JAVA_SHORT_ALIGNED, (Function<short[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42, new int[]{42}, JAVA_INT_ALIGNED, (Function<int[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42f, new float[]{42}, JAVA_FLOAT_ALIGNED, (Function<float[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42L, new long[]{42}, JAVA_LONG_ALIGNED, (Function<long[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, 42d, new double[]{42}, JAVA_DOUBLE_ALIGNED, (Function<double[], MemorySegment>)MemorySegment::ofArray });
            layouts.add(new Object[] { testCase.segment, testCase.align, MemoryAddress.ofLong(42), null, ADDRESS_ALIGNED, null });
        }
        return layouts.toArray(new Object[0][]);
    }
}
