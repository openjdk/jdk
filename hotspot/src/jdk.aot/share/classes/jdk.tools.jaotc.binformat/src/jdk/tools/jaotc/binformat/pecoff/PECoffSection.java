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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jdk.tools.jaotc.binformat.pecoff.PECoff;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SECTION_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

public class PECoffSection {
    ByteBuffer section;
    byte [] data;
    boolean hasrelocations;
    int sectionIndex;
    int align;

    public PECoffSection(String sectName, byte [] sectData, int sectFlags,
                         boolean hasRelocations, int sectIndex) {

        section = PECoffByteBuffer.allocate(IMAGE_SECTION_HEADER.totalsize);

        // bug: If JVM.oop.got section is empty, VM exits since JVM.oop.got
        //      symbol ends up as external forwarded reference.
        if (sectData.length == 0) sectData = new byte[8];

        // Copy only Max allowed bytes to Section Entry
        byte [] Name = sectName.getBytes();
        int max = Name.length <= IMAGE_SECTION_HEADER.Name.sz ?
                  Name.length : IMAGE_SECTION_HEADER.Name.sz;

        section.put(Name, IMAGE_SECTION_HEADER.Name.off, max);

        section.putInt(IMAGE_SECTION_HEADER.VirtualSize.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.VirtualAddress.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.SizeOfRawData.off, sectData.length);
        section.putInt(IMAGE_SECTION_HEADER.PointerToLinenumbers.off, 0);
        section.putChar(IMAGE_SECTION_HEADER.NumberOfLinenumbers.off, (char)0);

        section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, sectFlags);

        // Extract alignment from Characteristics field
        int alignshift = (sectFlags & IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_MASK) >>
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_SHIFT;

        // Use 8 byte alignment if not specified
        if (alignshift == 0)
            alignshift = 3;
        else
            --alignshift;

        align = 1 << alignshift;

        data = sectData;
        hasrelocations = hasRelocations;
        sectionIndex = sectIndex;
    }

    public long getSize() {
        return section.getInt(IMAGE_SECTION_HEADER.SizeOfRawData.off);
    }

    public int getDataAlign() {
        return (align);
    }

    // Alignment requirements for the IMAGE_SECTION_HEADER structures
    public static int getShdrAlign() {
        return (4);
    }

    public byte[] getArray() {
        return section.array();
    }

    public byte[] getDataArray() {
        return data;
    }

    public void setOffset(long offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRawData.off, (int)offset);
    }

    public long getOffset() {
        return (section.getInt(IMAGE_SECTION_HEADER.PointerToRawData.off));
    }

    public void setReloff(int offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRelocations.off, offset);
    }

    public void setRelcount(int count) {
        // If the number of relocs is larger than 65K, then set
        // the overflow bit.  The real count will be written to
        // the first reloc entry for this section.
        if (count > 0xFFFF) {
            int flags;
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char)0xFFFF);
            flags = section.getInt(IMAGE_SECTION_HEADER.Characteristics.off);
            flags |= IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_NRELOC_OVFL;
            section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, flags);
        }
        else {
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char)count);
        }
    }

    public boolean hasRelocations() {
        return hasrelocations;
    }

    public int getSectionId() {
        return sectionIndex;
    }

}


