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

/**
 * Provides carrier-thread-local storage for up to two buffer addresses.
 * It is caller's responsibility to store homogeneous segment sizes.
 * Storing addresses, not MemorySegments turns out to be slightly faster (write barrier?).
 */
public final class CallBufferCache {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static class PerThread {
        // Two-elements to support downcall + upcall.
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
            if (address1 != 0) CallBufferCache.free(address1);
            if (address2 != 0) CallBufferCache.free(address2);
        }
    }

    @SuppressWarnings("restricted")
    public static long allocate(long size) {
        return UNSAFE.allocateMemory(size);
    }

    public static void free(long address) {
        UNSAFE.freeMemory(address);
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
}
