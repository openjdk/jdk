/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import sun.jvm.hotspot.types.AddressField;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

public class ZForwardingTable extends VMObject {
    private static AddressField tableField;
    private static CIntegerField sizeField;

    static {
        VM.registerVMInitializedObserver((o, d) -> initialize(VM.getVM().getTypeDataBase()));
    }

    static private synchronized void initialize(TypeDataBase db) {
        Type type = db.lookupType("ZForwardingTable");

        tableField = type.getAddressField("_table");
        sizeField = type.getCIntegerField("_size");
    }

    public ZForwardingTable(Address addr) {
        super(addr);
    }

    Address table() {
        return tableField.getAddress(addr);
    }

    long size() {
        return sizeField.getJLong(addr);
    }

    ZForwardingTableEntry at(ZForwardingTableCursor cursor) {
        return new ZForwardingTableEntry(table().getAddressAt(cursor._value * VM.getVM().getBytesPerLong()));
    }

    ZForwardingTableEntry first(long from_index, ZForwardingTableCursor cursor) {
        long mask = size() - 1;
        long hash = ZHash.uint32_to_uint32(from_index);
        cursor._value = hash & mask;
        return at(cursor);
    }

    ZForwardingTableEntry next(ZForwardingTableCursor cursor) {
        long mask = size() - 1;
        cursor._value = (cursor._value + 1) & mask;
        return at(cursor);
    }

    ZForwardingTableEntry find(long from_index, ZForwardingTableCursor cursor) {
        // Reading entries in the table races with the atomic cas done for
        // insertion into the table. This is safe because each entry is at
        // most updated once (from -1 to something else).
        ZForwardingTableEntry entry = first(from_index, cursor);
        while (!entry.is_empty()) {
            if (entry.from_index() == from_index) {
                // Match found, return matching entry
                return entry;
            }

            entry = next(cursor);
        }

        // Match not found, return empty entry
        return entry;
    }

    ZForwardingTableEntry find(long from_index) {
        ZForwardingTableCursor dummy = new ZForwardingTableCursor();
        return find(from_index, dummy);
    }

    void dump() {
        long s = size();
        long count = 0;
        System.out.println("Dumping ZForwardingTable[" + s + "]:");
        ZForwardingTableCursor cursor = new ZForwardingTableCursor();
        for (long i = 0; i < s; i++) {
            cursor._value = i;
            ZForwardingTableEntry entry = at(cursor);
            if (!entry.is_empty()) {
                long hash = ZHash.uint32_to_uint32(entry.from_index());
                System.out.println(i + " " + count + " " + entry + " hash: " + hash + " masked_hash: " + (hash & (s - 1)));
                count++;
            }
        }
    }
}
