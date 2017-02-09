/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteOrder;

import jdk.tools.jaotc.binformat.elf.Elf;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Ehdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Shdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rel;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rela;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Sym;
import jdk.tools.jaotc.binformat.elf.ElfByteBuffer;

public class ElfSection {
    String name;
    ByteBuffer section;
    byte [] data;
    boolean hasrelocations;
    int sectionIndex;

    /**
     * String holding section name strings
     */
    private static StringBuilder sectNameTab = new StringBuilder();

    /**
     * Keeps track of bytes in section string table since strTabContent.length()
     * is number of chars, not bytes.
     */
    private static int shStrTabNrOfBytes = 0;

    public ElfSection(String sectName, byte [] sectData, int sectFlags,
                      int sectType, boolean hasRelocations, int sectIndex) {

        long align;

        section = ElfByteBuffer.allocate(Elf64_Shdr.totalsize);

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
        name = sectName;

        section.putInt(Elf64_Shdr.sh_type.off, sectType);
        section.putLong(Elf64_Shdr.sh_flags.off, sectFlags);
        section.putLong(Elf64_Shdr.sh_addr.off, 0);
        section.putLong(Elf64_Shdr.sh_offset.off, 0);

        if (sectName.equals(".shstrtab")) {
            section.putLong(Elf64_Shdr.sh_size.off, shStrTabNrOfBytes);
            data = sectNameTab.toString().getBytes();
        }
        else {
            data = sectData;
            section.putLong(Elf64_Shdr.sh_size.off, sectData.length);
        }

        section.putLong(Elf64_Shdr.sh_entsize.off, 0);

        // Determine the alignment and entrysize
        // based on type of section
        switch (sectType) {
            case Elf64_Shdr.SHT_PROGBITS:
                if ((sectFlags & Elf64_Shdr.SHF_EXECINSTR) != 0)
                    align = 16;
                else
                    align = 4;
                break;
            case Elf64_Shdr.SHT_SYMTAB:
                align = 8;
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Sym.totalsize);
                break;
            case Elf64_Shdr.SHT_STRTAB:
                align = 1;
                break;
            case Elf64_Shdr.SHT_RELA:
                align = 8;
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Rela.totalsize);
                break;
            case Elf64_Shdr.SHT_REL:
                align = 8;
                section.putLong(Elf64_Shdr.sh_entsize.off, Elf64_Rel.totalsize);
                break;
            case Elf64_Shdr.SHT_NOBITS:
                align = 4;
                break;
            default:
                align = 8;
                break;
        }
        section.putLong(Elf64_Shdr.sh_addralign.off, align);

        hasrelocations = hasRelocations;
        sectionIndex = sectIndex;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return section.getLong(Elf64_Shdr.sh_size.off);
    }

    public int getDataAlign() {
        return ((int)section.getLong(Elf64_Shdr.sh_addralign.off));
    }

    // Alignment requirements for the Elf64_Shdr structures
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
        section.putLong(Elf64_Shdr.sh_offset.off, offset);
    }

    public void setLink(int link) {
        section.putInt(Elf64_Shdr.sh_link.off, link);
    }

    public void setInfo(int info) {
        section.putInt(Elf64_Shdr.sh_info.off, info);
    }

    public long getOffset() {
        return (section.getLong(Elf64_Shdr.sh_offset.off));
    }

    public boolean hasRelocations() {
        return hasrelocations;
    }

    public int getSectionId() {
        return sectionIndex;
    }

}


