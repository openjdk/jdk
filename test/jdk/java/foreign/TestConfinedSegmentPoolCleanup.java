/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Cleanup actions must run before a confined arena releases its pool
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestConfinedSegmentPoolCleanup
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class TestConfinedSegmentPoolCleanup {

    private static final long MARKER = 0xF25C214F82C1422FL;

    @ParameterizedTest(name = "{0}, cleanupThrows={2}")
    @MethodSource("configurations")
    void cleanupRunsBeforePoolRelease(String name, Thread.Builder builder, boolean cleanupThrows) throws Throwable {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // It does not matter if a VT is running on a previously-used carrier thread.
        Thread thread = builder.start(() -> {
            try {
                testCleanup(cleanupThrows);
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });

        thread.join();
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static void testCleanup(boolean cleanupThrows) {
        Arena arena = Arena.ofConfined();
        long address;
        try {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            address = segment.address();
            segment.reinterpret(Long.BYTES, arena, cleanupSegment -> {
                cleanupSegment.set(ValueLayout.JAVA_LONG, 0, MARKER);
                if (cleanupThrows) {
                    throw new ExpectedCleanupException();
                }
            });

            if (cleanupThrows) {
                assertThrows(ExpectedCleanupException.class, arena::close);
            } else {
                arena.close();
            }
            assertFalse(arena.scope().isAlive());
        } finally {
            // Tidy up if something failed
            if (arena.scope().isAlive()) {
                arena.close();
            }
        }

        try (Arena verificationArena = Arena.ofConfined()) {
            MemorySegment segment = verificationArena.allocate(ValueLayout.JAVA_LONG);
            assertEquals(address, segment.address(), "pool was not reused");
            assertEquals(0L, segment.get(ValueLayout.JAVA_LONG, 0), "cleanup action contaminated the released pool");
        }
    }

    private static Stream<Arguments> configurations() {
        return Stream.of(
                Arguments.of("platform", Thread.ofPlatform(), false),
                Arguments.of("platform", Thread.ofPlatform(), true),
                Arguments.of("virtual", Thread.ofVirtual(), false),
                Arguments.of("virtual", Thread.ofVirtual(), true));
    }

    private static final class ExpectedCleanupException extends RuntimeException { }
}
