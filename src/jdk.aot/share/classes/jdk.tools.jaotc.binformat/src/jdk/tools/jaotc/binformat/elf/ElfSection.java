/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc.binformat.elf;

import java.nio.ByteBuffer;

import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rel;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rela;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Shdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Sym;

final class ElfSection {
    private final String name;
    private final ByteBuffer section;
    private final byte[] data;
    private final boolean hasrelocations;
    private final int sectionIndex;

    /**
     * String holding section name strings.
     */
    private static final StringBuilder sectNameTab = new StringBuilder();

    /**
     * Keeps track of bytes in section string table since strTabContent.length() is number of chars,
     * not bytes.
     */
    private static int shStrTabNrOfBytes = 0;

    ElfSection(String sectName, byte[] sectData, int sectFlags, int sectType,
                    boolean hasRelocations, int align, int sectIndex) {

        section = ElfByteBuffer.allocate(Elf64_Shdr.totalsize);
        name = sectName;
        // Return all 0's for NULL section
        if (sectIndex == 0) {
            sectNameTab.append('\0');
            shStrTabNrOfBytes += 1;
            data = null;
            hasrelocations = false;
            sectionIndex = 0;
            return;
        }

        section.putInt(Elf64_Shdr.sh_name.off, shStrTabNrOfBytes);
        sectNameTab.append(sectName).append('\0');
        shStrTabNrOfBytes += (sectName.getBytes().length + 1);

        section.putInt(Elf64_Shdr.sh_type.off, sectType);
        section.putLong(Elf64_Shdr.sh_flags.off, sectFlags);
        section.putLong(Elf64_Shdr.sh_addr.off, 0);
        section.putLong(Elf64_Shdr.sh_offset.off, 0);

        if (sectName.equals(".shstrtab")) {
            section.putLong(Elf64_Shdr.sh_size.off, shStrTabNrOfBytes);
            data = sectNameTab.toString().getBytes();
        } else {
            data = sectData;
            section.putLong(Elf64_Shdr.sh_size.off, sectData.length);
        }

        section.putLong(Elf64_Shdr.sh_entsize.off, 0);

        // Determine the entrysize
        // based on type of section
        switch (sectType) {
            case Elf64_Shdr.SHT_SYMTAB:
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Sym.totalsize);
                break;
            case Elf64_Shdr.SHT_RELA:
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Rela.totalsize);
                break;
            case Elf64_Shdr.SHT_REL:
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Rel.totalsize);
                break;
            default:
                break;
        }
        section.putLong(Elf64_Shdr.sh_addralign.off, align);

        hasrelocations = hasRelocations;
        sectionIndex = sectIndex;
    }

    String getName() {
        return name;
    }

    long getSize() {
        return section.getLong(Elf64_Shdr.sh_size.off);
    }

    int getDataAlign() {
        return ((int) section.getLong(Elf64_Shdr.sh_addralign.off));
    }

    // Alignment requirements for the Elf64_Shdr structures
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
        section.putLong(Elf64_Shdr.sh_offset.off, offset);
    }

    void setLink(int link) {
        section.putInt(Elf64_Shdr.sh_link.off, link);
    }

    void setInfo(int info) {
        section.putInt(Elf64_Shdr.sh_info.off, info);
    }

    long getOffset() {
        return (section.getLong(Elf64_Shdr.sh_offset.off));
    }

    boolean hasRelocations() {
        return hasrelocations;
    }

    int getSectionId() {
        return sectionIndex;
    }

}
