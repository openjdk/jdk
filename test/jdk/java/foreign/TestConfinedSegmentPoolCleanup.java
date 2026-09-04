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
 * @modules java.base/jdk.internal.foreign java.base/jdk.internal.access
 * @build TestConfinedSegmentPoolUtils
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestConfinedSegmentPoolCleanup
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class TestConfinedSegmentPoolCleanup {

    // A random value
    private static final long MARKER = 0xF25C214F82C1422FL;

    @ParameterizedTest(name = "{0}, throwableType={2}")
    @MethodSource("configurations")
    <T extends Throwable & ExpectedCleanupThrowable> void cleanupRunsBeforePoolRelease(String name, Thread.Builder builder, Class<T> throwableType) throws Throwable {
        // It does not matter if a VT is running on a previously-used carrier thread.
        TestConfinedSegmentPoolUtils.runOn(builder, () -> testCleanup(throwableType));
    }

    private static <T extends Throwable & ExpectedCleanupThrowable> void testCleanup(Class<T> throwableType) {
        Arena arena = Arena.ofConfined();
        long address;
        try {
            MemorySegment segment = arena.allocate(ValueLayout.JAVA_LONG);
            address = segment.address();
            segment.reinterpret(Long.BYTES, arena, cleanupSegment -> {
                cleanupSegment.set(ValueLayout.JAVA_LONG, 0, MARKER);
                switch (throwableType) {
                    case Class<?> c when ExpectedCleanupException.class.equals(c) -> throw new ExpectedCleanupException();
                    case Class<?> c when ExpectedCleanupError.class.equals(c) -> throw new ExpectedCleanupError();
                    case null, default -> { } // Do nothing
                }
            });

            if (throwableType != null) {
                assertThrows(throwableType, arena::close);
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
                Arguments.of("platform", Thread.ofPlatform(), null),
                Arguments.of("platform", Thread.ofPlatform(), ExpectedCleanupException.class),
                Arguments.of("platform", Thread.ofPlatform(), ExpectedCleanupError.class),
                Arguments.of("virtual", Thread.ofVirtual(), null),
                Arguments.of("virtual", Thread.ofVirtual(), ExpectedCleanupException.class),
                Arguments.of("virtual", Thread.ofVirtual(), ExpectedCleanupError.class));
    }

    private sealed interface ExpectedCleanupThrowable{};
    private static final class ExpectedCleanupException extends RuntimeException implements ExpectedCleanupThrowable {}
    private static final class ExpectedCleanupError extends Error implements ExpectedCleanupThrowable {}
}
