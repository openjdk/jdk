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
 * @run junit/othervm --add-opens=java.base/java.lang=ALL-UNNAMED
 *                    -Djava.lang.foreign.native.confined.arena.pool-slots=0
 *                    TestConfinedSegmentPoolNoPool
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

final class TestConfinedSegmentPoolNoPool {

    static final Field THREAD_ALLOCATOR_FIELD;

    static {
        try {
            THREAD_ALLOCATOR_FIELD = Thread.class.getDeclaredField("confinedArenaAllocator");
            THREAD_ALLOCATOR_FIELD.setAccessible(true);
        } catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Test
    void testConfinedArenaFallsBackWhenPoolDisabled() throws Exception {
        assertNull(threadAllocator(Thread.currentThread()));

        MemorySegment segment;
        try (Arena arena = Arena.ofConfined()) {
            assertEquals("ArenaImpl", arena.getClass().getSimpleName());
            assertNull(threadAllocator(Thread.currentThread()));

            segment = arena.allocate(ValueLayout.JAVA_LONG);
            assertSame(arena.scope(), segment.scope());
            segment.set(ValueLayout.JAVA_LONG, 0, 42L);
            assertEquals(42L, segment.get(ValueLayout.JAVA_LONG, 0));
        }

        assertThrows(IllegalStateException.class,
                () -> segment.get(ValueLayout.JAVA_LONG, 0));
        assertNull(threadAllocator(Thread.currentThread()));
    }

    static Object threadAllocator(Thread thread) throws ReflectiveOperationException {
        return THREAD_ALLOCATOR_FIELD.get(thread);
    }
}
