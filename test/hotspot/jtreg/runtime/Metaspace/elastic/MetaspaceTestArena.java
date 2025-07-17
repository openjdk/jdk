/*
 * Copyright (c) 2020, 2023, SAP SE. All rights reserved.
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import jdk.test.whitebox.WhiteBox;

public class MetaspaceTestArena {

    long arena;

    final long allocationCeiling;

    // Number and byte size of allocations
    long allocatedBytes = 0;
    long numAllocated = 0;
    long deallocatedBytes = 0;
    long numDeallocated = 0;
    volatile long numAllocationFailures = 0;

    private synchronized boolean reachedCeiling() {
        return (allocatedBytes - deallocatedBytes) > allocationCeiling;
    }

    private synchronized void accountAllocation(long size) {
        numAllocated ++;
        allocatedBytes += size;
    }

    private synchronized void accountDeallocation(long size) {
        numDeallocated ++;
        deallocatedBytes += size;
    }

    MetaspaceTestArena(long arena0, long allocationCeiling) {
        this.allocationCeiling = allocationCeiling;
        this.arena = arena0;
    }

    public Allocation allocate(long size) {
        if (reachedCeiling()) {
            numAllocationFailures ++;
            return null;
        }
        WhiteBox wb = WhiteBox.getWhiteBox();
        long p = wb.allocateFromMetaspaceTestArena(arena, size);
        if (p == 0) {
            numAllocationFailures ++;
            return null;
        } else {
            accountAllocation(size);
        }
        return new Allocation(p, size);
    }

    public void deallocate(Allocation a) {
        WhiteBox wb = WhiteBox.getWhiteBox();
        wb.deallocateToMetaspaceTestArena(arena, a.p, a.size);
        accountDeallocation(a.size);
    }

    //// Convenience functions ////

    public Allocation allocate_expect_success(long size) {
        Allocation a = allocate(size);
        if (a.isNull()) {
            throw new RuntimeException("Allocation failed (" + size + ")");
        }
        return a;
    }

    public void allocate_expect_failure(long size) {
        Allocation a = allocate(size);
        if (!a.isNull()) {
            throw new RuntimeException("Allocation failed (" + size + ")");
        }
    }

    boolean isLive() {
        return arena != 0;
    }

    @Override
    public String toString() {
        return "arena=" + arena +
                ", ceiling=" + allocationCeiling +
                ", allocatedBytes=" + allocatedBytes +
                ", numAllocated=" + numAllocated +
                ", deallocatedBytes=" + deallocatedBytes +
                ", numDeallocated=" + numDeallocated +
                ", numAllocationFailures=" + numAllocationFailures +
                '}';
    }

}
