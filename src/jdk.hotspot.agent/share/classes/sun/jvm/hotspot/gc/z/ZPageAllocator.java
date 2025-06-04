/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.z;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for ZPageAllocator

public class ZPageAllocator extends VMObject {

    private static CIntegerField maxCapacityField;
    private static long partitionsOffset;
    private static long numaCount;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("ZPageAllocator");

        maxCapacityField = type.getCIntegerField("_max_capacity");
        partitionsOffset = type.getAddressField("_partitions").getOffset();
        numaCount = ZNUMA.count();
    }

    private ZPerNUMAZPartition partitions() {
        Address partitionsAddr = addr.addOffsetTo(partitionsOffset);
        return VMObjectFactory.newObject(ZPerNUMAZPartition.class, partitionsAddr);
    }

    public long maxCapacity() {
        return maxCapacityField.getValue(addr);
    }

    public long capacity() {
        long total_capacity = 0;
        for (int id = 0; id < numaCount; id++) {
          total_capacity += partitions().value(id).capacity();
        }
        return total_capacity;
    }

    public long used() {
        long total_used = 0;
        for (int id = 0; id < numaCount; id++) {
          total_used += partitions().value(id).used();
        }
        return total_used;
    }

    public ZPageAllocator(Address addr) {
        super(addr);
    }
}
