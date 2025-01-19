/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign.abi
 * @run testng/othervm --enable-native-access=ALL-UNNAMED CallBufferCacheTest
 */

import jdk.internal.foreign.abi.CallBufferCache;
import org.testng.annotations.Test;

import java.lang.foreign.MemorySegment;

import static jdk.internal.foreign.abi.CallBufferCache.CACHED_BUFFER_SIZE;
import static org.testng.Assert.*;

public class CallBufferCacheTest {

    @Test
    public void testEmpty() {
        assertNull(CallBufferCache.acquire());
    }

    private void testAllocate(long size, long expectedSize) {
        MemorySegment segment1 = CallBufferCache.allocate(size);
        MemorySegment segment2 = CallBufferCache.allocate(size);
        assertEquals(segment1.byteSize(), expectedSize);
        assertEquals(segment2.byteSize(), expectedSize);
        assertNotSame(segment1, segment2);
        assertNotSame(segment1.address(), segment2.address());
        assertTrue(segment1.asOverlappingSlice(segment2).isEmpty());
        CallBufferCache.free(segment1);
        CallBufferCache.free(segment2);
    }

    @Test
    public void testAllocateSmall() {
        testAllocate(1, CACHED_BUFFER_SIZE);
    }

    @Test
    public void testAllocateLarge() {
        testAllocate(CACHED_BUFFER_SIZE + 123, CACHED_BUFFER_SIZE + 123);
    }

    @Test
    public void testCacheSize() {
        assertNull(CallBufferCache.acquire());

        MemorySegment segment1 = CallBufferCache.allocate(128);
        MemorySegment segment2 = CallBufferCache.allocate(128);
        MemorySegment segment3 = CallBufferCache.allocate(128);

        assertTrue(CallBufferCache.release(segment3));
        assertTrue(CallBufferCache.release(segment2));
        assertFalse(CallBufferCache.release(segment1));

        MemorySegment first = CallBufferCache.acquire();
        assertTrue(first == segment3 || first == segment2);
        assertTrue(CallBufferCache.release(first));

        first = CallBufferCache.acquire();
        MemorySegment second = CallBufferCache.acquire();
        assertNotSame(first, second);
        assertTrue(first == segment2 || first == segment3);
        assertTrue(second == segment2 || second == segment3);

        assertNull(CallBufferCache.acquire());

        CallBufferCache.free(segment1);
        CallBufferCache.free(segment2);
        CallBufferCache.free(segment3);
    }

    @Test
    public void testThreadLocal() throws InterruptedException {
        MemorySegment segment = CallBufferCache.allocate(128);
        assertTrue(CallBufferCache.release(segment));
        Thread.ofPlatform().start(() -> assertNull(CallBufferCache.acquire())).join();
        assertSame(segment, CallBufferCache.acquire());
        CallBufferCache.free(segment);
    }

    @Test
    public void testMigrateThread() throws InterruptedException {
        MemorySegment segment = CallBufferCache.allocate(128);
        assertTrue(CallBufferCache.release(segment));
        assertSame(segment, CallBufferCache.acquire());
        Thread.ofPlatform().start(() -> {
            CallBufferCache.release(segment);
            assertSame(segment, CallBufferCache.acquire());
            CallBufferCache.release(segment);
        }).join();
        assertNull(CallBufferCache.acquire());
    }
}
