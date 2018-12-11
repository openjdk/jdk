/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Ehdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Shdr;

final class ElfHeader {
    private final ByteBuffer header;

    ElfHeader() {
        header = ElfByteBuffer.allocate(Elf64_Ehdr.totalsize);

        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_MAG0, Elf64_Ehdr.ELFMAG0);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_MAG1, Elf64_Ehdr.ELFMAG1);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_MAG2, Elf64_Ehdr.ELFMAG2);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_MAG3, Elf64_Ehdr.ELFMAG3);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_CLASS, Elf64_Ehdr.ELFCLASS64);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_DATA, Elf64_Ehdr.ELFDATA2LSB);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_VERSION, Elf64_Ehdr.EV_CURRENT);
        header.put(Elf64_Ehdr.e_ident.off + Elf64_Ehdr.EI_OSABI, Elf64_Ehdr.ELFOSABI_NONE);

        header.putChar(Elf64_Ehdr.e_type.off, Elf64_Ehdr.ET_REL);
        header.putChar(Elf64_Ehdr.e_machine.off, ElfTargetInfo.getElfArch());
        header.putInt(Elf64_Ehdr.e_version.off, Elf64_Ehdr.EV_CURRENT);
        header.putChar(Elf64_Ehdr.e_ehsize.off, (char) Elf64_Ehdr.totalsize);
        header.putChar(Elf64_Ehdr.e_shentsize.off, (char) Elf64_Shdr.totalsize);

    }

    // Update header with file offset of first section
    void setSectionOff(int offset) {
        header.putLong(Elf64_Ehdr.e_shoff.off, offset);
    }

    // Update header with the number of total sections
    void setSectionNum(int count) {
        header.putChar(Elf64_Ehdr.e_shnum.off, (char) count);
    }

    // Update header with the section index containing the
    // string table for section names
    void setSectionStrNdx(int index) {
        header.putChar(Elf64_Ehdr.e_shstrndx.off, (char) index);
    }

    byte[] getArray() {
        return header.array();
    }
}
