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

package jdk.internal.foreign;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySegment.Scope;

import jdk.internal.foreign.ArenaImpl;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.internal.foreign.NativeMemorySegmentImpl;

/**
 * f
 */
public class NativeMemoryTracking {
    /**
     * f
     */
    public static native long makeTag(String name);
    /**
     * f
     */
    public static native void paintAllocation(long size, long mem_tag);

    /**
     * f
     */
    NativeMemoryTracking() {}

    private static native void registerNatives();
    static {
        runtimeSetup();
    }

    // Unsafe.java has this comment: Called from JVM when loading an AOT cache
    // so we imitate the same pattern, maybe it's necessary for us as well???
    private static void runtimeSetup() {
        registerNatives();
    }

    /**
     * f
     */
    public static class NMTArena implements Arena {
        private ArenaImpl arena;
        private long mem_tag;

        /**
         * f
         */
        public NMTArena(MemorySessionImpl session, String name) {
            this.mem_tag = NativeMemoryTracking.makeTag(name);
            this.arena = new ArenaImpl(session);
        }

        @Override
        public Scope scope() {
            return arena.scope();
        }

        @Override
        public void close() {
            arena.close();
        }

        @Override
        public NativeMemorySegmentImpl allocate(long byteSize, long byteAlignment) {
            NativeMemorySegmentImpl nmsi = arena.allocate(byteSize, byteAlignment);
            long address = nmsi.address();
            NativeMemoryTracking.paintAllocation(address, this.mem_tag);
            return nmsi;
        }
    }
}
