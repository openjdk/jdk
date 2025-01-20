/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.foreign.abi  java.base/jdk.internal.misc
 * @run testng/othervm --enable-native-access=ALL-UNNAMED CallBufferCacheTest
 */

import jdk.internal.foreign.abi.CallBufferCache;
import jdk.internal.misc.Unsafe;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class CallBufferCacheTest {
    Unsafe UNSAFE = Unsafe.getUnsafe();
    
    @Test
    public void testEmpty() {
        assertEquals(CallBufferCache.acquire(), 0);
    }

    @Test
    public void testCacheSize() {
        assertEquals(CallBufferCache.acquire(), 0);

        // Three nested calls.
        long address1 = UNSAFE.allocateMemory(128);
        long address2 = UNSAFE.allocateMemory(128);
        long address3 = UNSAFE.allocateMemory(128);

        // Two buffers go into the cache.
        assertTrue(CallBufferCache.release(address3));
        assertTrue(CallBufferCache.release(address2));
        assertFalse(CallBufferCache.release(address1));

        // Next acquisition is either of them.
        long first = CallBufferCache.acquire();
        assertTrue(first == address3 || first == address2);
        assertTrue(CallBufferCache.release(first));

        // Can re-acquire both.
        first = CallBufferCache.acquire();
        long second = CallBufferCache.acquire();
        assertNotEquals(first, second);
        assertTrue(first == address2 || first == address3);
        assertTrue(second == address2 || second == address3);
        // Now the cache is empty again.
        assertEquals(CallBufferCache.acquire(), 0);

        UNSAFE.freeMemory(address1);
        UNSAFE.freeMemory(address2);
        UNSAFE.freeMemory(address3);
    }

    @Test
    public void testThreadLocal() throws InterruptedException {
        long address = UNSAFE.allocateMemory(128);
        assertTrue(CallBufferCache.release(address));
        Thread.ofPlatform().start(() -> {
            // Not visible in other thread.
            assertEquals(CallBufferCache.acquire(), 0);
        }).join();
        // Only here.
        assertEquals(address, CallBufferCache.acquire());
        UNSAFE.freeMemory(address);
    }

    @Test
    public void testMigrateThread() throws InterruptedException {
        long address = UNSAFE.allocateMemory(128);
        assertTrue(CallBufferCache.release(address));
        assertEquals(address, CallBufferCache.acquire());
        Thread.ofPlatform().start(() -> {
            // A buffer can migrate to another thread due to VThread scheduling.
            CallBufferCache.release(address);
            assertEquals(address, CallBufferCache.acquire());
            CallBufferCache.release(address);
            // freed by TL.
        }).join();
        assertEquals(CallBufferCache.acquire(), 0);
    }
}
