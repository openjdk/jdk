/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit TestSegmentHash
 */

import org.junit.jupiter.api.*;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentHash {

    private static final MemorySegment SEGMENT = MemorySegment.ofArray(
            new byte[]{8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23})
            .asReadOnly();

    @Test
    void testZeroLengthHash() {
        // This test assumes zero is returned for zero length hashes
        for (int i = 0; i <SEGMENT.byteSize(); i++) {
            assertEquals(0, MemorySegment.contentHash(SEGMENT, i, i));
        }
    }

    @Test
    void testHashDiffers() {
        // This test is extremely likely to pass
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i <SEGMENT.byteSize(); i++) {
            assertTrue(seen.add(MemorySegment.contentHash(SEGMENT, 0, i)));
        }
    }

    @Test
    void testSlices() {
        for (int i = 0; i <SEGMENT.byteSize(); i++) {
            MemorySegment slice = SEGMENT.asSlice(i);
            long expected = MemorySegment.contentHash(SEGMENT, i, SEGMENT.byteSize());
            long actual = MemorySegment.contentHash(slice, 0, slice.byteSize());
            assertEquals(expected, actual);
        }
    }

    @Test
    void testInvariants() throws InterruptedException {
        assertThrows(NullPointerException.class, () -> MemorySegment.contentHash(null, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> MemorySegment.contentHash(SEGMENT, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> MemorySegment.contentHash(SEGMENT, 2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> MemorySegment.contentHash(SEGMENT, SEGMENT.byteSize(), SEGMENT.byteSize() + 1));

        MemorySegment seg;
        try (var arena = Arena.ofConfined()) {
            seg = arena.allocate(JAVA_LONG);

            AtomicReference<WrongThreadException> e = new AtomicReference<>();
            Thread t = Thread.ofPlatform().start(() ->
                    e.set(assertThrows(WrongThreadException.class, () -> {
                        MemorySegment.contentHash(seg, 0, seg.byteSize());
                    })));
            t.join();
            assertNotNull(e.get());
        }

        // Check a closed scope
        assertThrows(IllegalStateException.class, () -> MemorySegment.contentHash(seg, 0, seg.byteSize()));
    }

    // Apply hash for a segment wrapper class

    public static final GroupLayout POINT = MemoryLayout.structLayout(JAVA_INT.withName("x"), JAVA_INT.withName("y"));
    public static final VarHandle X = POINT.varHandle(MemoryLayout.PathElement.groupElement("x"));
    public static final VarHandle Y = POINT.varHandle(MemoryLayout.PathElement.groupElement("y"));

    public interface Point {
        int x();
        void x(int x);
        int y();
        void y(int y);
    }

    public class PointImpl implements Point {

        private final MemorySegment segment;

        public PointImpl(Arena arena) {
            segment = arena.allocate(POINT);
        }

        @Override
        public int x() {
            return (int) X.get(segment, 0);
        }

        @Override
        public void x(int x) {
            X.set(segment, 0, x);
        }

        @Override
        public int y() {
            return (int) Y.get(segment, 0);
        }

        @Override
        public void y(int y) {
            Y.set(segment, 0, y);
        }

        @Override
        public String toString() {
            return "[x=" + x() + ", y=" + y() + "]";
        }

        @Override
        public int hashCode() {
            return Long.hashCode(MemorySegment.contentHash(segment, 0, POINT.byteSize()));
        }

        public int hashCode2() {
            return Objects.hash(x(), y());
        }

        public int hashCode3() {
            int result = 0;
            result = 31 * result + x();
            result = 31 * result + y();
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            return (obj instanceof Point other) &&
                    x() == other.x() &&
                    y() == other.x();
        }
    }

    @Test
    void testWrapper() {
        try (Arena arena = Arena.ofConfined()) {
            Point p = new PointImpl(arena);
            p.x(3);
            p.y(4);
            int h = p.hashCode();
            assertNotEquals(0, h);
        }
    }

}
