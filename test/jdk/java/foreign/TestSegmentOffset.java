/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit TestSegmentOffset
 */

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static java.lang.System.out;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentOffset {

    @ParameterizedTest
    @MethodSource("slices")
    void testOffset(SegmentSlice s1, SegmentSlice s2) {
        if (s1.contains(s2)) {
            // check that a segment and its overlapping segment point to same elements
            long offset = s1.offset(s2);
            for (int i = 0; i < s2.size(); i++) {
                out.format("testOffset s1:%s, s2:%s, offset:%d, i:%s\n", s1, s2, offset, i);
                byte expected = s2.segment.get(JAVA_BYTE, i);
                byte found = s1.segment.get(JAVA_BYTE, i + offset);
                assertEquals(expected, found);
            }
        } else if (!s2.contains(s1)) {
            // disjoint segments - check that offset is out of bounds
            long offset = s1.offset(s2);
            for (int i = 0; i < s2.size(); i++) {
                out.format("testOffset s1:%s, s2:%s, offset:%d, i:%s\n", s1, s2, offset, i);
                s2.segment.get(JAVA_BYTE, i);
                try {
                    s1.segment.get(JAVA_BYTE, i + offset);
                    fail("Offset on a disjoint segment is not out of bounds!");
                } catch (IndexOutOfBoundsException ex) {
                    assertTrue(true);
                }
            }
        }
    }

    final static class SegmentSlice {

        enum Kind {
            NATIVE(i -> Arena.ofAuto().allocate(i, 1)),
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
        final int first;
        final int last;
        final MemorySegment segment;

        SegmentSlice(Kind kind, int first, int last, MemorySegment segment) {
            this.kind = kind;
            this.first = first;
            this.last = last;
            this.segment = segment;
        }

        boolean contains(SegmentSlice other) {
            return kind == other.kind &&
                    first <= other.first &&
                    last >= other.last;
        }

        int size() {
            return last - first + 1;
        }

        long offset(SegmentSlice that) {
            return that.segment.address() - segment.address();
        }
    }


    static Stream<Arguments> slices() {
        int[] sizes = { 16, 8, 4, 2, 1 };
        List<SegmentSlice> slices = new ArrayList<>();
        for (SegmentSlice.Kind kind : SegmentSlice.Kind.values()) {
            // init root segment
            MemorySegment segment = kind.makeSegment(16);
            for (int i = 0 ; i < 16 ; i++) {
                segment.set(JAVA_BYTE, i, (byte)i);
            }
            // compute all slices
            for (int size : sizes) {
                for (int index = 0 ; index < 16 ; index += size) {
                    MemorySegment slice = segment.asSlice(index, size);
                    slices.add(new SegmentSlice(kind, index, index + size - 1, slice));
                }
            }
        }
        SegmentSlice[][] sliceArray = new SegmentSlice[slices.size() * slices.size()][];
        for (int i = 0 ; i < slices.size() ; i++) {
            for (int j = 0 ; j < slices.size() ; j++) {
                sliceArray[i * slices.size() + j] = new SegmentSlice[] { slices.get(i), slices.get(j) };
            }
        }
        return Arrays.stream(sliceArray)
                // Only consider segment slices of the same kind
                .filter(a -> a[0].kind == a[1].kind)
                .map(Arguments::of);
    }
}
