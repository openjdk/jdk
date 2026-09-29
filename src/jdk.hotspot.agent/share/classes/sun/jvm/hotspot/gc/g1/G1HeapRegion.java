/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.g1;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.gc.shared.ContiguousSpace;
import sun.jvm.hotspot.gc.shared.LiveRegionsProvider;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for G1HeapRegion. Currently we don't actually include
// any of its fields but only iterate over it.

public class G1HeapRegion extends ContiguousSpace implements LiveRegionsProvider {
    private static AddressField bottomField;
    private static AddressField topField;
    private static AddressField endField;

    private static CIntegerField grainBytesField;
    private static CIntegerField pinnedCountField;

    private static long typeFieldOffset;
    private static long pointerSize;

    private G1HeapRegionType type;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("G1HeapRegion");

        bottomField = type.getAddressField("_bottom");
        topField = type.getAddressField("_top");
        endField = type.getAddressField("_end");

        grainBytesField = type.getCIntegerField("GrainBytes");
        pinnedCountField = type.getCIntegerField("_pinned_object_count");

        typeFieldOffset = type.getField("_type").getOffset();

        pointerSize = db.lookupType("G1HeapRegion*").getSize();
    }

    public static long grainBytes() {
        return grainBytesField.getValue();
    }

    public G1HeapRegion(Address addr) {
        super(addr);
        Address typeAddr = (addr instanceof OopHandle) ? addr.addOffsetToAsOopHandle(typeFieldOffset)
                                                       : addr.addOffsetTo(typeFieldOffset);
        type = VMObjectFactory.newObject(G1HeapRegionType.class, typeAddr);
    }

    public Address bottom()        { return bottomField.getValue(addr); }
    public Address top()           { return topField.getValue(addr); }
    public Address end()           { return endField.getValue(addr); }

    @Override
    public List<MemRegion> getLiveRegions() {
        List<MemRegion> res = new ArrayList<>();
        res.add(new MemRegion(bottom(), top()));
        return res;
    }

    /** Returns a subregion of the space containing all the objects in
        the space. */
    public MemRegion usedRegion() {
        return new MemRegion(bottom(), end());
    }

    public long used() {
        return top().minus(bottom());
    }

    public long free() {
        return end().minus(top());
    }

    public boolean isFree() {
        return type.isFree();
    }

    public boolean isYoung() {
        return type.isYoung();
    }

    public boolean isHumongous() {
        return type.isHumongous();
    }

    public boolean isPinned() {
        return pinnedCountField.getValue(addr) != 0;
    }

    public boolean isOld() {
        return type.isOld();
    }

    public static long getPointerSize() {
        return pointerSize;
    }

    public boolean isInRegion(Address addr) {
        return (addr.greaterThanOrEqual(bottom()) && addr.lessThan(end()));
    }

    public void printOn(PrintStream tty) {
        tty.print("Region: " + bottom() + "," + top() + "," + end());
        tty.println(":" + type.typeAnnotation() + (isPinned() ? " Pinned" : ""));
    }
}
