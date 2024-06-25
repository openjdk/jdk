/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import sun.jvm.hotspot.utilities.Observable;
import sun.jvm.hotspot.utilities.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.debugger.OopHandle;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.runtime.VMObjectFactory;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for G1HeapRegionTable. It's essentially an index -> G1HeapRegion map.

public class G1HeapRegionTable extends VMObject {
    // G1HeapRegion** _base;
    private static AddressField baseField;
    // uint _length;
    private static CIntegerField lengthField;
    // G1HeapRegion** _biased_base
    private static AddressField biasedBaseField;
    // size_t _bias
    private static CIntegerField biasField;
    // uint _shift_by
    private static CIntegerField shiftByField;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    private static synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("G1HeapRegionTable");

        baseField = type.getAddressField("_base");
        lengthField = type.getCIntegerField("_length");
        biasedBaseField = type.getAddressField("_biased_base");
        biasField = type.getCIntegerField("_bias");
        shiftByField = type.getCIntegerField("_shift_by");
    }

    private G1HeapRegion at(long index) {
        Address arrayAddr = baseField.getValue(addr);
        // Offset of &_base[index]
        long offset = index * VM.getVM().getAddressSize();
        Address regionAddr = arrayAddr.getAddressAt(offset);
        return VMObjectFactory.newObject(G1HeapRegion.class, regionAddr);
    }

    public long length() {
        return lengthField.getValue(addr);
    }

    public long bias() {
        return biasField.getValue(addr);
    }

    public long shiftBy() {
        return shiftByField.getValue(addr);
    }

    private class G1HeapRegionIterator implements Iterator<G1HeapRegion> {
        private long index;
        private long length;
        private G1HeapRegion next;

        public G1HeapRegion positionToNext() {
          G1HeapRegion result = next;
          while (index < length && at(index) == null) {
            index++;
          }
          if (index < length) {
            next = at(index);
            index++; // restart search at next element
          } else {
            next = null;
          }
          return result;
        }

        @Override
        public boolean hasNext() { return next != null;     }

        @Override
        public G1HeapRegion next() { return positionToNext(); }

        @Override
        public void remove()     { /* not supported */      }

        G1HeapRegionIterator(long totalLength) {
            index = 0;
            length = totalLength;
            positionToNext();
        }
    }

    public Iterator<G1HeapRegion> heapRegionIterator(long committedLength) {
        return new G1HeapRegionIterator(committedLength);
    }

    public G1HeapRegionTable(Address addr) {
        super(addr);
    }

    public G1HeapRegion getByAddress(Address target) {
        Address arrayAddr = biasedBaseField.getValue(addr);
        long biasedIndex = target.asLongValue() >>> shiftBy();
        long offset = biasedIndex * G1HeapRegion.getPointerSize();
        Address regionAddr = arrayAddr.getAddressAt(offset);
        return VMObjectFactory.newObject(G1HeapRegion.class, regionAddr);
    }
}
