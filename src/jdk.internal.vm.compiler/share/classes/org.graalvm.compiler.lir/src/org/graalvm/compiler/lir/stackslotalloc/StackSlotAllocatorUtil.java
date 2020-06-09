/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.stackslotalloc;

import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.StackSlot;

/**
 * A stack slot allocator is responsible for translating {@link VirtualStackSlot virtual} stack
 * slots into {@link StackSlot real} stack slots. This includes changing all occurrences of
 * {@link VirtualStackSlot} in the {@link LIRGenerationResult#getLIR() LIR} to {@link StackSlot}.
 */
public final class StackSlotAllocatorUtil {
    /**
     * The number of allocated stack slots.
     */
    public static final CounterKey allocatedSlots = DebugContext.counter("StackSlotAllocator[allocatedSlots]");
    /**
     * The number of reused stack slots.
     */
    public static final CounterKey reusedSlots = DebugContext.counter("StackSlotAllocator[reusedSlots]");
    /**
     * The size (in bytes) required for all allocated stack slots. Note that this number corresponds
     * to the actual frame size and might include alignment.
     */
    public static final CounterKey allocatedFramesize = DebugContext.counter("StackSlotAllocator[AllocatedFramesize]");
    /** The size (in bytes) required for all virtual stack slots. */
    public static final CounterKey virtualFramesize = DebugContext.counter("StackSlotAllocator[VirtualFramesize]");
}
