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
    public final static ScopedValue<Long> TAG;
    /**
     * f
     */
    private static native long makeTag(String name);
    /**
     * f
     */
    private static native long allocate0(long size, long mem_tag);

    /**
     * f
     */
    NativeMemoryTracking() {}

    private static native void registerNatives();

    static {
        runtimeSetup();
        TAG = ScopedValue.newInstance();
    }

    private static long currentTag() {
        long mtNone = 27;
        return TAG.orElse(mtNone);
    }

    public static long allocate(long size) {
        return allocate0(size, currentTag());
    }

    public record Tag(long tag) {}

    public static Tag resolveName(String name) {
        return new Tag(makeTag(name));
    }

    public static ScopedValue.Carrier where(Tag name) {
        return ScopedValue.where(TAG, name.tag);
    }

    // Unsafe.java has this comment: Called from JVM when loading an AOT cache
    // so we imitate the same pattern, maybe it's necessary for us as well???
    private static void runtimeSetup() {
        registerNatives();
    }
}
