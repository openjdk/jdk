/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.gc_implementation.g1;

import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for HeapRegionSeq. It's essentially an index -> HeapRegion map.

public class HeapRegionSeq extends VMObject {
    // HeapRegion** _regions;
    static private AddressField regionsField;
    // size_t _length;
    static private CIntegerField lengthField;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("HeapRegionSeq");

        regionsField = type.getAddressField("_regions");
        lengthField = type.getCIntegerField("_length");
    }

    private HeapRegion at(long index) {
        Address arrayAddr = regionsField.getValue(addr);
        // Offset of &_region[index]
        long offset = index * VM.getVM().getAddressSize();
        Address regionAddr = arrayAddr.getAddressAt(offset);
        return (HeapRegion) VMObjectFactory.newObject(HeapRegion.class,
                                                      regionAddr);
    }

    public long length() {
        return lengthField.getValue(addr);
    }

    private class HeapRegionIterator implements Iterator<HeapRegion> {
        private long index;
        private long length;

        @Override
        public boolean hasNext() { return index < length; }

        @Override
        public HeapRegion next() { return at(index++);    }

        @Override
        public void remove()     { /* not supported */    }

        HeapRegionIterator(Address addr) {
            index = 0;
            length = length();
        }
    }

    public Iterator<HeapRegion> heapRegionIterator() {
        return new HeapRegionIterator(addr);
    }

    public HeapRegionSeq(Address addr) {
        super(addr);
    }
}
