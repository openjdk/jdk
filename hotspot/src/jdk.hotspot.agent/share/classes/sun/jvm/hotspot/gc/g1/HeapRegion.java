/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.gc.shared.CompactibleSpace;
import sun.jvm.hotspot.memory.MemRegion;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

// Mirror class for HeapRegion. Currently we don't actually include
// any of its fields but only iterate over it.

public class HeapRegion extends CompactibleSpace {
    // static int GrainBytes;
    static private CIntegerField grainBytesField;
    static private AddressField topField;

    static {
        VM.registerVMInitializedObserver(new Observer() {
                public void update(Observable o, Object data) {
                    initialize(VM.getVM().getTypeDataBase());
                }
            });
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("HeapRegion");

        grainBytesField = type.getCIntegerField("GrainBytes");
        topField = type.getAddressField("_top");

    }

    static public long grainBytes() {
        return grainBytesField.getValue();
    }

    public HeapRegion(Address addr) {
        super(addr);
    }

    public Address top() {
        return topField.getValue(addr);
    }

    @Override
    public List getLiveRegions() {
        List res = new ArrayList();
        res.add(new MemRegion(bottom(), top()));
        return res;
    }

    @Override
    public long used() {
        return top().minus(bottom());
    }

    @Override
    public long free() {
        return end().minus(top());
    }
}
