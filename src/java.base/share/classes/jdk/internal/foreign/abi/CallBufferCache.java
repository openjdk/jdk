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
package jdk.internal.foreign.abi;

import jdk.internal.misc.TerminatingThreadLocal;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.Continuation;

import java.lang.foreign.MemorySegment;

/**
 * Provides carrier-thread-local storage for up to two small buffers.
 */
public final class CallBufferCache {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static class PerThread {
        // Two-elements to support downcall + upcall.
        // Storing addresses, not MemorySegments turns out to be slightly faster (write barrier?).
        private long address1;
        private long address2;

        long pop() {
            if (address1 != 0) {
                long result = address1;
                address1 = 0;
                return result;
            }
            if (address2 != 0) {
                long result = address2;
                address2 = 0;
                return result;
            }
            return 0;
        }

        boolean push(long address) {
            if (address1 == 0) {
                address1 = address;
                return true;
            }
            if (address2 == 0) {
                address2 = address;
                return true;
            }
            return false;
        }

        void free() {
            if (address1 != 0) UNSAFE.freeMemory(address1);
            if (address2 != 0) UNSAFE.freeMemory(address2);
        }
    }

    private static final TerminatingThreadLocal<PerThread> tl = new TerminatingThreadLocal<>() {
        @Override
        protected PerThread initialValue() {
            return new PerThread();
        }

        @Override
        protected void threadTerminated(PerThread cache) {
            cache.free();
        }
    };

    // acquire/release visible only for tests

    public static long acquire() {
        // Protect against vthread unmount.
        Continuation.pin();
        try {
            return tl.get().pop();
        } finally {
            Continuation.unpin();
        }
    }

    public static boolean release(long address) {
        // Protect against vthread unmount.
        Continuation.pin();
        try {
            return tl.get().push(address);
        } finally {
            Continuation.unpin();
        }
    }

    private static final long CACHED_BUFFER_SIZE = 256;

    @SuppressWarnings("restricted")
    public static MemorySegment acquireOrAllocate(long requestedSize) {
        final long bufferSize = Math.max(requestedSize, CACHED_BUFFER_SIZE);
        long address = (bufferSize == CACHED_BUFFER_SIZE) ? acquire() : 0;
        if (address == 0) {
            // Either size was too large or cache empty.
            address = UNSAFE.allocateMemory(bufferSize);
        }
        return MemorySegment.ofAddress(address).reinterpret(requestedSize);
    }

    public static void releaseOrFree(MemorySegment segment) {
        if (segment.byteSize() > CACHED_BUFFER_SIZE || !release(segment.address())) {
            // Either size was too large or cache full.
            UNSAFE.freeMemory(segment.address());
        }
    }
}
