/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;

/**
 * This is a source with different versions for various JDKs. When modifying/adding a field in this
 * class accessed from outside this class, be sure to update the field appropriately in all source
 * files named {@code GraalHotSpotVMConfigVersioned.java}.
 *
 * Fields are grouped according to the most recent JBS issue showing why they are versioned.
 *
 * JDK Version: 12+
 */
final class GraalHotSpotVMConfigVersioned extends HotSpotVMConfigAccess {

    GraalHotSpotVMConfigVersioned(HotSpotVMConfigStore store) {
        super(store);
    }

    // JDK-8210848
    boolean inlineNotify = true;

    // JDK-8073583
    boolean useCRC32CIntrinsics = getFlag("UseCRC32CIntrinsics", Boolean.class);

    // JDK-8046936
    int javaThreadReservedStackActivationOffset = getFieldOffset("JavaThread::_reserved_stack_activation", Integer.class, "address");
    int methodFlagsOffset = getFieldOffset("Method::_flags", Integer.class, "u2");
    long throwDelayedStackOverflowErrorEntry = getFieldValue("StubRoutines::_throw_delayed_StackOverflowError_entry", Long.class, "address");
    long enableStackReservedZoneAddress = getAddress("SharedRuntime::enable_stack_reserved_zone");

    // JDK-8135085
    int methodIntrinsicIdOffset = getFieldOffset("Method::_intrinsic_id", Integer.class, "u2");

    // JDK-8151956
    int methodCodeOffset = getFieldOffset("Method::_code", Integer.class, "CompiledMethod*");

    // JDK-8059606
    int invocationCounterIncrement = getConstant("InvocationCounter::count_increment", Integer.class);
    int invocationCounterShift = getConstant("InvocationCounter::count_shift", Integer.class);

    // JDK-8195142
    byte dirtyCardValue = getConstant("CardTable::dirty_card", Byte.class);
    byte g1YoungCardValue = getConstant("G1CardTable::g1_young_gen", Byte.class);

    // JDK-8201318
    int g1SATBQueueMarkingOffset = getConstant("G1ThreadLocalData::satb_mark_queue_active_offset", Integer.class);
    int g1SATBQueueIndexOffset = getConstant("G1ThreadLocalData::satb_mark_queue_index_offset", Integer.class);
    int g1SATBQueueBufferOffset = getConstant("G1ThreadLocalData::satb_mark_queue_buffer_offset", Integer.class);
    int g1CardQueueIndexOffset = getConstant("G1ThreadLocalData::dirty_card_queue_index_offset", Integer.class);
    int g1CardQueueBufferOffset = getConstant("G1ThreadLocalData::dirty_card_queue_buffer_offset", Integer.class);

    // JDK-8033552
    long heapTopAddress = getFieldValue("CompilerToVM::Data::_heap_top_addr", Long.class, "HeapWord* volatile*");

    // JDK-8015774
    long codeCacheLowBound = getFieldValue("CodeCache::_low_bound", Long.class, "address");
    long codeCacheHighBound = getFieldValue("CodeCache::_high_bound", Long.class, "address");
}
