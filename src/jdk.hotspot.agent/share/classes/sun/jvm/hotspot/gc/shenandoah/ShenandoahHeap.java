/*
 * Copyright (c) 2017, 2021, Red Hat, Inc. All rights reserved.
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

import sun.jvm.hotspot.gc.shared.CollectedHeap;
import sun.jvm.hotspot.gc.shared.CollectedHeapName;
import sun.jvm.hotspot.gc.shared.LiveRegionsClosure;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.utilities.BitMapInterface;

import java.io.PrintStream;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

public class ShenandoahHeap extends CollectedHeap {
    private static CIntegerField numRegions;
    private static AddressField  globalFreeSet;
    private static AddressField  allocatorField;
    private static CIntegerField committed;
    private static AddressField  regions;
    private static CIntegerField logMinObjAlignmentInBytes;

    private static long regionPtrFieldSize;
    static {
        VM.registerVMInitializedObserver(new Observer() {
            public void update(Observable o, Object data) {
                initialize(VM.getVM().getTypeDataBase());
            }
        });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("ShenandoahHeap");
        numRegions = type.getCIntegerField("_num_regions");
        globalFreeSet = type.getAddressField("_free_set");
        allocatorField = type.getAddressField("_allocator");
        committed = type.getCIntegerField("_committed");
        regions = type.getAddressField("_regions");
        logMinObjAlignmentInBytes = type.getCIntegerField("_log_min_obj_alignment_in_bytes");

        Type regionPtrType = db.lookupType("ShenandoahHeapRegion*");
        regionPtrFieldSize = regionPtrType.getSize();
    }

    public ShenandoahHeap(Address addr) {
        super(addr);
    }

    @Override
    public CollectedHeapName kind() {
        return CollectedHeapName.SHENANDOAH;
    }

    public long numOfRegions() {
        return numRegions.getValue(addr);
    }

    @Override
    public long capacity() {
        return numOfRegions() * ShenandoahHeapRegion.regionSizeBytes();
    }

    @Override
    public long used() {
        Address globalFreeSetAddress = globalFreeSet.getValue(addr);
        ShenandoahFreeSet freeset = VMObjectFactory.newObject(ShenandoahFreeSet.class, globalFreeSetAddress);
        // The free set stores raw used with each active CAS alloc region's entire remaining capacity
        // pre-charged at reserve time. In-process readers subtract the still-unconsumed remnant (see
        // ShenandoahFreeSet::corrected_used / alloc_region_correction); mirror that here so the SA
        // reports the same used as the live MemoryMXBean rather than an inflated value. The
        // subtraction is saturating for the same reason it is in C++: the raw used is a snapshot
        // while the per-region remnant is read live.
        //
        // The correction sums only the allocator's cached alloc regions (at most a handful of
        // bounded stripe slots per partition), reached via the allocator rather than by walking
        // every heap region -- so used() stays O(slots) and does not regress to O(num_regions),
        // which would make a jhsdb used() query crawl (and risk OOM) on a multi-TB heap.
        long rawUsed = freeset.used();
        long correction = allocator().activeAllocRegionFree();
        return rawUsed > correction ? rawUsed - correction : 0;
    }

    public ShenandoahAllocator allocator() {
        return VMObjectFactory.newObject(ShenandoahAllocator.class, allocatorField.getValue(addr));
    }

    public long committed() {
        return committed.getValue(addr);
    }

    public int getLogMinObjAlignmentInBytes() {
        return logMinObjAlignmentInBytes.getJInt(addr);
    }

    public ShenandoahHeapRegion getRegion(long index) {
        if (index < numOfRegions()) {
            Address arrayAddr = regions.getValue(addr);
            Address regAddr = arrayAddr.getAddressAt(index * regionPtrFieldSize);
            ShenandoahHeapRegion region = VMObjectFactory.newObject(ShenandoahHeapRegion.class, regAddr);
            region.setHeap(this);
            return region;
        }
        return null;
    }

    public ShenandoahHeapRegion regionAtOffset(long offset) {
        long index = offset >>> ShenandoahHeapRegion.regionSizeBytesShift();
        if (index < 0 || index >= numOfRegions()) {
            throw new RuntimeException("Invalid offset: " + offset);
        }
        return getRegion(index);
    }

    @Override
    public void liveRegionsIterate(LiveRegionsClosure closure) {
        for (long index = 0; index < numOfRegions(); index ++) {
            ShenandoahHeapRegion region = getRegion(index);
            closure.doLiveRegions(region);
        }
    }

    @Override
    public void printOn(PrintStream tty) {
        MemRegion mr = reservedRegion();
        tty.print("Shenandoah heap");
        tty.print(" [" + mr.start() + ", " + mr.end() + "]");
        tty.println(" region size " + ShenandoahHeapRegion.regionSizeBytes() / 1024 + " K");
    }

    @Override
    public BitMapInterface createBitMap(long bits) {
        return new ShenandoahBitMap(this);
    }

}
