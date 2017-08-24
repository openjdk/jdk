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

import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SECTION_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffSection {
    private final ByteBuffer section;
    private final byte[] data;
    private final boolean hasrelocations;
    private final int sectionIndex;
    private final int align;

    PECoffSection(String sectName, byte[] sectData0, int sectFlags0, int sectAlign, boolean hasRelocations, int sectIndex) {

        section = PECoffByteBuffer.allocate(IMAGE_SECTION_HEADER.totalsize);

        // If .oop.got section is empty, VM exits since .oop.got
        // symbol ends up as external forwarded reference.
        byte[] sectData = sectData0;
        if (sectData0.length == 0) {
            sectData = new byte[8];
        }

        // Copy only Max allowed bytes to Section Entry
        byte[] Name = sectName.getBytes();
        int max = Name.length <= IMAGE_SECTION_HEADER.Name.sz ? Name.length : IMAGE_SECTION_HEADER.Name.sz;

        assert !(sectAlign < 1 || sectAlign > 1024 || (sectAlign & (sectAlign - 1)) != 0) : "section alignment is not valid: " + sectAlign;
        align = sectAlign;

        // Using 32 because IMAGE_SCN_ALIGN_*BYTES is value + 1
        int sectAlignBits = (32 - Integer.numberOfLeadingZeros(align)) << IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_SHIFT;
        // Clear and set alignment bits
        int sectFlags = (sectFlags0 & ~IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_MASK) | (sectAlignBits & IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_MASK);

        section.put(Name, IMAGE_SECTION_HEADER.Name.off, max);

        section.putInt(IMAGE_SECTION_HEADER.VirtualSize.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.VirtualAddress.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.SizeOfRawData.off, sectData.length);
        section.putInt(IMAGE_SECTION_HEADER.PointerToLinenumbers.off, 0);
        section.putChar(IMAGE_SECTION_HEADER.NumberOfLinenumbers.off, (char) 0);

        section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, sectFlags);

        data = sectData;
        hasrelocations = hasRelocations;
        sectionIndex = sectIndex;
    }

    long getSize() {
        return section.getInt(IMAGE_SECTION_HEADER.SizeOfRawData.off);
    }

    int getDataAlign() {
        return (align);
    }

    // Alignment requirements for the IMAGE_SECTION_HEADER structures
    static int getShdrAlign() {
        return (4);
    }

    byte[] getArray() {
        return section.array();
    }

    byte[] getDataArray() {
        return data;
    }

    void setOffset(long offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRawData.off, (int) offset);
    }

    long getOffset() {
        return (section.getInt(IMAGE_SECTION_HEADER.PointerToRawData.off));
    }

    void setReloff(int offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRelocations.off, offset);
    }

    void setRelcount(int count) {
        // If the number of relocs is larger than 65K, then set
        // the overflow bit. The real count will be written to
        // the first reloc entry for this section.
        if (count > 0xFFFF) {
            int flags;
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char) 0xFFFF);
            flags = section.getInt(IMAGE_SECTION_HEADER.Characteristics.off);
            flags |= IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_NRELOC_OVFL;
            section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, flags);
        } else {
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char) count);
        }
    }

    boolean hasRelocations() {
        return hasrelocations;
    }

    int getSectionId() {
        return sectionIndex;
    }

}
