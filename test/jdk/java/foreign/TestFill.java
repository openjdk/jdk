/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test MemorySegment::fill
 * @run junit TestFill
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class TestFill {

    // Make sure negative values are treated as expected
    private static final byte VALUE = -71;

    @ParameterizedTest
    @MethodSource("sizes")
    void testFill(int len) {
        int offset = 16;
        int expandedLen = offset + MAX_SIZE + offset;

        // Make sure fill only affects the intended region XXXXXX
        //
        // ................XXXXXX................
        // |    offset     | len |    offset     |

        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(expandedLen);
            var slice = segment.asSlice(offset, len);
            slice.fill(VALUE);

            var expected = new byte[expandedLen];
            Arrays.fill(expected, offset, offset + len, VALUE);

            // This checks the actual fill region as well as potential under and overflows
            assertArrayEquals(expected, segment.toArray(ValueLayout.JAVA_BYTE));
        }
    }

    @ParameterizedTest
    @MethodSource("values")
    void testValues(int value) {
        int size = 0b1111;
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(size);
            segment.fill((byte) value);
            assertTrue(segment.elements(ValueLayout.JAVA_BYTE)
                    .map(s -> s.get(ValueLayout.JAVA_BYTE, 0))
                    .allMatch(v -> v == value), "Failed to fill with value " + value);
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void testReadOnly(int len) {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(len).asReadOnly();
            assertThrows(IllegalArgumentException.class, () -> segment.fill(VALUE));
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void testConfinement(int len) {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(len);
            AtomicReference<RuntimeException> ex = new AtomicReference<>();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    segment.fill(VALUE);
                } catch (RuntimeException e) {
                    ex.set(e);
                }
            });
            future.join();
            assertInstanceOf(WrongThreadException.class, ex.get());
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void testScope(int len) {
        var arena = Arena.ofConfined();
        var segment = arena.allocate(len);
        arena.close();
        assertThrows(IllegalStateException.class, () -> segment.fill(VALUE));
    }

    private static final int MAX_SIZE = 1 << 10;

    private static Stream<Arguments> sizes() {
        return IntStream.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 23, 32, 63, 128, 256, 511, MAX_SIZE)
                .boxed()
                .map(Arguments::of);
    }

    private static Stream<Arguments> values() {
        return IntStream.rangeClosed(Byte.MIN_VALUE, Byte.MAX_VALUE)
                .boxed()
                .map(Arguments::of);
    }

}
