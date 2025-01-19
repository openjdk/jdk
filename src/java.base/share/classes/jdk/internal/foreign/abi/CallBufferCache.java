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
 * Allocates intermediate buffer space needed within call handles.
 * Small buffers may be cached in thread-local storage.
 */
public final class CallBufferCache {
    // Minimum allocation size = maximum cached size
    public static final int CACHED_BUFFER_SIZE = 256;
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static class PerThread {
        // Two-elements to support downcall + upcall. Elements are unscoped.
        private MemorySegment cached1;
        private MemorySegment cached2;

        MemorySegment pop() {
            if (cached1 != null) {
                MemorySegment result = cached1;
                cached1 = null;
                return result;
            }
            if (cached2 != null) {
                MemorySegment result = cached2;
                cached2 = null;
                return result;
            }
            return null;
        }

        boolean push(MemorySegment segment) {
            if (cached1 == null) {
                cached1 = segment;
                return true;
            }
            if (cached2 == null) {
                cached2 = segment;
                return true;
            }
            return false;
        }

        void free() {
            if (cached1 != null) CallBufferCache.free(cached1);
            if (cached2 != null) CallBufferCache.free(cached2);
        }
    }

    @SuppressWarnings("restricted")
    public static MemorySegment allocate(long size) {
        long allocatedSize = Math.max(CACHED_BUFFER_SIZE, size);
        return MemorySegment.ofAddress(UNSAFE.allocateMemory(allocatedSize)).reinterpret(allocatedSize);
    }

    public static void free(MemorySegment segment) {
        UNSAFE.freeMemory(segment.address());
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

    public static MemorySegment acquire() {
        Continuation.pin();
        try {
            return tl.get().pop();
        } finally {
            Continuation.unpin();
        }
    }

    public static boolean release(MemorySegment segment) {
        Continuation.pin();
        try {
            return tl.get().push(segment);
        } finally {
            Continuation.unpin();
        }
    }
}
