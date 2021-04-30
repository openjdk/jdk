/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc.x;

import java.io.PrintStream;
import java.util.Iterator;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.gc.shared.CollectedHeap;
import sun.jvm.hotspot.gc.shared.CollectedHeapName;
import sun.jvm.hotspot.gc.shared.LiveRegionsClosure;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;
import sun.jvm.hotspot.utilities.BitMapInterface;

// Mirror class for XCollectedHeap.

public class XCollectedHeap extends CollectedHeap {
    private static long zHeapFieldOffset;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("XCollectedHeap");

        zHeapFieldOffset = type.getAddressField("_heap").getOffset();
    }

    public XHeap heap() {
        Address heapAddr = addr.addOffsetTo(zHeapFieldOffset);
        return VMObjectFactory.newObject(XHeap.class, heapAddr);
    }

    @Override
    public CollectedHeapName kind() {
        return CollectedHeapName.Z;
    }

    @Override
    public void printOn(PrintStream tty) {
        heap().printOn(tty);
    }

    public XCollectedHeap(Address addr) {
        super(addr);
    }

    @Override
    public long capacity() {
        return heap().capacity();
    }

    @Override
    public long used() {
        return heap().used();
    }

    @Override
    public boolean isInReserved(Address a) {
        return heap().isIn(a);
    }

    private OopHandle oop_load_barrier(Address oopAddress) {
        oopAddress = XBarrier.weak_barrier(oopAddress);
        if (oopAddress == null) {
            return null;
        }

        return oopAddress.addOffsetToAsOopHandle(0);
    }

    @Override
    public OopHandle oop_load_at(OopHandle handle, long offset) {
        assert(!VM.getVM().isCompressedOopsEnabled());

        Address oopAddress = handle.getAddressAt(offset);

        return oop_load_barrier(oopAddress);
    }

    // addr can be either in heap or in native
    @Override
    public OopHandle oop_load_in_native(Address addr) {
        Address oopAddress = addr.getAddressAt(0);
        return oop_load_barrier(oopAddress);
    }

    public String oopAddressDescription(OopHandle handle) {
        Address origOop = XOop.to_address(handle);
        Address loadBarrieredOop = XBarrier.weak_barrier(origOop);
        if (!origOop.equals(loadBarrieredOop)) {
            return origOop + " (" + loadBarrieredOop.toString() + ")";
        } else {
            return handle.toString();
        }
    }

    @Override
    public void liveRegionsIterate(LiveRegionsClosure closure) {
        Iterator<XPage> iter = heap().pageTable().activePagesIterator();
        while (iter.hasNext()) {
            XPage page = iter.next();
            closure.doLiveRegions(page);
        }
    }

    @Override
    public BitMapInterface createBitMap(long size) {
        // Ignores the size
        return new XExternalBitMap(this);
    }
}
