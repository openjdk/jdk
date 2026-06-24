/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package sun.jvm.hotspot.gc.shenandoah;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// Mirror for ShenandoahAllocator. Holds the three per-partition allocators by value (mutator,
// collector, old-collector), each a striped array of cached CAS alloc regions. Its sole purpose
// in the SA is to let ShenandoahHeap.used() iterate only the bounded stripe slots (at most
// 3 * MAX_ALLOC_REGIONS) to compute the alloc-region accounting correction, rather than walking
// every heap region.
public class ShenandoahAllocator extends VMObject {
    private static long mutatorAllocOffset;
    private static long collectorAllocOffset;
    private static long oldCollectorAllocOffset;

    static {
        VM.registerVMInitializedObserver(new Observer() {
            public void update(Observable o, Object data) {
                initialize(VM.getVM().getTypeDataBase());
            }
        });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("ShenandoahAllocator");
        // The partition allocators are embedded by value, so resolve their byte offsets and
        // construct the mirror at addr+offset (the per-value sub-object idiom used elsewhere in SA).
        mutatorAllocOffset      = type.getField("_mutator_alloc").getOffset();
        collectorAllocOffset    = type.getField("_collector_alloc").getOffset();
        oldCollectorAllocOffset = type.getField("_old_collector_alloc").getOffset();
    }

    public ShenandoahAllocator(Address addr) {
        super(addr);
    }

    private ShenandoahPartitionAllocator partitionAllocator(long offset) {
        return VMObjectFactory.newObject(ShenandoahPartitionAllocator.class, addr.addOffsetTo(offset));
    }

    // Sum, across all three partitions' stripe slots, of the bytes pre-charged to used at reserve
    // time but not yet consumed. Mirrors ShenandoahAllocator::active_alloc_region_free over every
    // partition. O(number of stripe slots), independent of the heap's region count.
    public long activeAllocRegionFree() {
        return partitionAllocator(mutatorAllocOffset).activeAllocRegionFree()
             + partitionAllocator(collectorAllocOffset).activeAllocRegionFree()
             + partitionAllocator(oldCollectorAllocOffset).activeAllocRegionFree();
    }
}
