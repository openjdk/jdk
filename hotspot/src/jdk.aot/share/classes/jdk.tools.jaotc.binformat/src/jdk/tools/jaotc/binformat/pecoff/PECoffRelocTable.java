/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 */

package jdk.tools.jaotc.binformat.pecoff;

import java.util.ArrayList;
import java.nio.ByteBuffer;

import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_RELOCATION;
import jdk.tools.jaotc.binformat.pecoff.PECoffRelocEntry;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffRelocTable {
    ArrayList<ArrayList<PECoffRelocEntry>> relocEntries;

    PECoffRelocTable(int numsects) {
        relocEntries = new ArrayList<>(numsects);
        for (int i = 0; i < numsects; i++) {
            relocEntries.add(new ArrayList<PECoffRelocEntry>());
        }
    }

    void createRelocationEntry(int sectindex, int offset, int symno, int type) {
        PECoffRelocEntry entry = new PECoffRelocEntry(offset, symno, type);
        relocEntries.get(sectindex).add(entry);
    }

    static int getAlign() {
        return (4);
    }

    int getNumRelocs(int section_index) {
        return relocEntries.get(section_index).size();
    }

    // Return the relocation entries for a single section
    // or null if no entries added to section
    byte[] getRelocData(int section_index) {
        ArrayList<PECoffRelocEntry> entryList = relocEntries.get(section_index);
        int entryCount = entryList.size();
        int allocCount = entryCount;

        if (entryCount == 0) {
            return null;
        }
        if (entryCount > 0xFFFF) {
            allocCount++;
        }
        ByteBuffer relocData = PECoffByteBuffer.allocate(allocCount * IMAGE_RELOCATION.totalsize);

        // If number of relocs exceeds 65K, add the real size
        // in a dummy first reloc entry
        if (entryCount > 0xFFFF) {
            PECoffRelocEntry entry = new PECoffRelocEntry(allocCount, 0, 0);
            relocData.put(entry.getArray());
        }

        // Copy each entry to a single ByteBuffer
        for (int i = 0; i < entryCount; i++) {
            PECoffRelocEntry entry = entryList.get(i);
            relocData.put(entry.getArray());
        }

        return (relocData.array());
    }
}
