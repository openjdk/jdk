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
import java.lang.foreign.MemorySegment;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

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

}
