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
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

// Mirror for ShenandoahPartitionAllocator<PARTITION>. Caches a striped array of alloc regions
// (_alloc_regions[_alloc_region_count], each an Atomic<ShenandoahHeapRegion*>); a thread bumps a
// region's atomic top lock-free. All three template instantiations (mutator, collector,
// old-collector) share an identical layout, so a single SA mirror reads any of them.
public class ShenandoahPartitionAllocator extends VMObject {
    private static CIntegerField allocRegionCountField;
    private static long          allocRegionsBaseOffset;
    private static long          allocRegionPtrSize;

    static {
        VM.registerVMInitializedObserver(new Observer() {
            public void update(Observable o, Object data) {
                initialize(VM.getVM().getTypeDataBase());
            }
        });
    }

    private static synchronized void initialize(TypeDataBase db) {
        // The three template instantiations have identical layout; the mutator typedef stands in.
        Type type = db.lookupType("ShenandoahMutatorPartitionAllocator");
        allocRegionCountField  = type.getCIntegerField("_alloc_region_count");
        allocRegionsBaseOffset = type.getField("_alloc_regions[0]").getOffset();
        // Each slot is an Atomic<ShenandoahHeapRegion*>, whose storage is a single pointer.
        allocRegionPtrSize = VM.getVM().getAddressSize();
    }

    public ShenandoahPartitionAllocator(Address addr) {
        super(addr);
    }

    public long allocRegionCount() {
        return allocRegionCountField.getValue(addr);
    }

    // Sum of the still-unconsumed pre-charged remnant across this partition's stripe slots. Mirrors
    // ShenandoahPartitionAllocator::remnant_bytes: iterate the slots, and for each
    // non-null cached region add its active-alloc-region remnant (end - atomic_top). O(slot count).
    public long remnantBytes() {
        long total = 0;
        long count = allocRegionCount();
        for (long i = 0; i < count; i++) {
            Address slotAddr = addr.addOffsetTo(allocRegionsBaseOffset + i * allocRegionPtrSize);
            Address regionAddr = slotAddr.getAddressAt(0);
            if (regionAddr == null) {
                continue;
            }
            ShenandoahHeapRegion region = VMObjectFactory.newObject(ShenandoahHeapRegion.class, regionAddr);
            total += region.remnantBytes();
        }
        return total;
    }
}
