/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.gc.shared.LiveRegionsProvider;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.oops.Oop;
import sun.jvm.hotspot.oops.UnknownOopException;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

public class ZPage extends VMObject implements LiveRegionsProvider {
    private static CIntegerField typeField;
    private static CIntegerField seqnumField;
    private static long virtualFieldOffset;
    private static AddressField topField;
    private static CIntegerField refcountField;
    private static long forwardingFieldOffset;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("ZPage");

        typeField = type.getCIntegerField("_type");
        seqnumField = type.getCIntegerField("_seqnum");
        virtualFieldOffset = type.getField("_virtual").getOffset();
        topField = type.getAddressField("_top");
        refcountField = type.getCIntegerField("_refcount");
        forwardingFieldOffset = type.getField("_forwarding").getOffset();
    }

    public ZPage(Address addr) {
        super(addr);
    }

    private byte type() {
        return typeField.getJByte(addr);
    }

    private int seqnum() {
        return seqnumField.getJInt(addr);
    }

    private ZVirtualMemory virtual() {
        return VMObjectFactory.newObject(ZVirtualMemory.class, addr.addOffsetTo(virtualFieldOffset));
    }

    private Address top() {
        return topField.getValue(addr);
    }

    private int refcount() {
        // refcount is uint32_t so need to be cautious when using this field.
        return refcountField.getJInt(addr);
    }

    private ZForwardingTable forwarding() {
        return VMObjectFactory.newObject(ZForwardingTable.class, addr.addOffsetTo(forwardingFieldOffset));
    }

    private boolean is_forwarding() {
        return forwarding().table() != null;
    }

    private boolean is_relocatable() {
        return is_active() && seqnum() < ZGlobals.ZGlobalSeqNum();
    }

    private boolean isPageRelocating() {
        assert(is_active());
        // is_forwarding():  Has a (relocation) forwarding table
        // is_relocatable(): Has not been freed yet
        return is_forwarding() && is_relocatable();
    }

    long start() {
        return virtual().start();
    }

    long size() {
        return virtual().end() - virtual().start();
    }

    Address forward_object(Address from) {
        // Lookup address in forwarding table
        long from_offset = ZAddress.offset(from);
        long from_index = (from_offset - start()) >> object_alignment_shift();
        ZForwardingTableEntry entry = forwarding().find(from_index);
        assert(!entry.is_empty());
        assert(entry.from_index() == from_index);

        return ZAddress.good(entry.to_offset());
    }

    Address relocate_object(Address from) {
        // Lookup address in forwarding table
        long from_offset = ZAddress.offset(from);
        long from_index = (from_offset - start()) >> object_alignment_shift();
        ZForwardingTableEntry entry = forwarding().find(from_index);
        if (!entry.is_empty() && entry.from_index() == from_index) {
          return ZAddress.good(entry.to_offset());
        }

        // There's no relocate operation in the SA.
        // Mimic object pinning and return the good view of the from object.
        return ZAddress.good(from);
    }

    long object_alignment_shift() {
        if (type() == ZGlobals.ZPageTypeSmall) {
            return ZGlobals.ZObjectAlignmentSmallShift();
        } else if (type() == ZGlobals.ZPageTypeMedium) {
            return ZGlobals.ZObjectAlignmentMediumShift;
        } else {
            assert(type() == ZGlobals.ZPageTypeLarge);
            return ZGlobals.ZObjectAlignmentLargeShift;
        }
    }

    long objectAlignmentSize() {
        return 1 << object_alignment_shift();
    }

    public boolean is_active() {
        return refcount() != 0;
    }

    private long getObjectSize(Address good) {
        OopHandle handle = good.addOffsetToAsOopHandle(0);
        Oop obj = null;

        try {
           obj = VM.getVM().getObjectHeap().newOop(handle);
        } catch (UnknownOopException exp) {
          throw new RuntimeException(" UnknownOopException  " + exp);
        }

        return VM.getVM().alignUp(obj.getObjectSize(), objectAlignmentSize());
    }

    private void addNotRelocatedRegions(List<MemRegion> regions) {
        MemRegion mr = null;

        // Some objects have already been forwarded to new locations.
        long topValue = top().asLongValue();
        for (long offsetValue = start(); offsetValue < topValue;) {
            Address from = ZAddress.good(ZUtils.longToAddress(offsetValue));

            Address to = relocate_object(from);

            long byteSize;
            try {
                byteSize = getObjectSize(to);
            } catch (Exception e) {
                // Parsing the ZHeap is inherently unsafe
                // when classes have been unloaded. Dead objects
                // might have stale Klass pointers, and there's
                // no way to get the size of the dead object.
                //
                // If possible, run with -XX:-ClassUnloading
                // to ensure that all Klasses are kept alive.
                System.err.println("Unparsable regions found. Skipping: "
                        + from
                        + " "
                        + from.addOffsetTo(topValue - offsetValue));

                // Can't proceed further. Just return the collected regions.
                return;
            }

            if (from.equals(to)) {
                // Not relocated - add region
                if (mr == null) {
                    mr = new MemRegion(from, 0 /* wordSize */);
                    regions.add(mr);
                }

                long wordSize = byteSize / VM.getVM().getBytesPerWord();
                mr.setWordSize(mr.wordSize() + wordSize);
            } else {
                // Forwarded somewhere else, split region.
                mr = null;
            }

            offsetValue += byteSize;
        }
    }

    public List<MemRegion> getLiveRegions() {
        List<MemRegion> res = new ArrayList<>();

        if (isPageRelocating()) {
            addNotRelocatedRegions(res);
        } else {
            Address start = ZAddress.good(ZUtils.longToAddress(start()));

            // Can't convert top() to a "good" address because it might
            // be at the top of the "offset" range, and therefore also
            // looks like one of the color bits. Instead use the "good"
            // address and add the size.
            long size = top().asLongValue() - start();
            Address end = start.addOffsetTo(size);

            res.add(new MemRegion(start, end));
        }

        return res;
    }
}
