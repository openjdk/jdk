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
 * @summary Test SegmentBulkOperations::contentHash
 * @modules java.base/jdk.internal.foreign
 * @run junit TestSegmentBulkOperationsContentHash
 */

import jdk.internal.foreign.AbstractMemorySegmentImpl;
import jdk.internal.foreign.SegmentBulkOperations;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class TestSegmentBulkOperationsContentHash {

    @ParameterizedTest
    @MethodSource("sizes")
    @Disabled
    void testHashValues(int len) {
        try (var arena = Arena.ofConfined()) {
            for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE ; i++) {
                var segment = arena.allocate(len);
                segment.fill((byte) i);
                int hash = hash(segment);
                int expected = Arrays.hashCode(segment.toArray(ValueLayout.JAVA_BYTE));
                assertEquals(expected, hash, Arrays.toString(segment.toArray(ValueLayout.JAVA_BYTE)));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void testOffsets(int len) {
        if (len < 2) {
            return;
        }
        try (var arena = Arena.ofConfined()) {
            for (int i = Byte.MIN_VALUE; i <= Byte.MAX_VALUE ; i++) {
                var segment = arena.allocate(len);
                segment.fill((byte) i);
                int hash = hash(segment, 1, segment.byteSize() - 1);
                MemorySegment slice = segment.asSlice(1, segment.byteSize() - 2);
                byte[] arr = slice.toArray(ValueLayout.JAVA_BYTE);
                System.out.println(Arrays.toString(arr));
                int expected = Arrays.hashCode(arr);
                assertEquals(expected, hash, Arrays.toString(segment.toArray(ValueLayout.JAVA_BYTE)));
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    void testOutOfBounds(int len) {
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(len);
            assertThrows(IndexOutOfBoundsException.class,
                    () -> hash(segment, 0, segment.byteSize() + 1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> hash(segment, 0, -1));
            assertThrows(IndexOutOfBoundsException.class,
                    () -> hash(segment, -1, 0));
            if (len > 2) {
                assertThrows(IndexOutOfBoundsException.class,
                        () -> hash(segment, 2, 1));
            }
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
                    hash(segment);
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
        assertThrows(IllegalStateException.class, () -> hash(segment));
    }

    private static int hash(MemorySegment segment) {
        return hash(segment, 0, segment.byteSize());
    }

    private static int hash(MemorySegment segment, long fromOffset, long toOffset) {
        return SegmentBulkOperations.contentHash((AbstractMemorySegmentImpl) segment, fromOffset, toOffset);
    }

    private static final int MAX_SIZE = 1 << 10;

    private static Stream<Arguments> sizes() {
        return IntStream.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 23, 32, 63, 128, 256, 511, MAX_SIZE)
                .boxed()
                .map(Arguments::of);
    }

}
