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

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Ehdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rela;

public class AMD64JELFRelocObject extends JELFRelocObject {

    AMD64JELFRelocObject(BinaryContainer binContainer, String outputFileName) {
        super(binContainer, outputFileName);
    }

    @Override
    protected void createRelocation(Symbol symbol, Relocation reloc, ElfRelocTable elfRelocTable) {
        RelocType relocType = reloc.getType();

        int elfRelocType = getELFRelocationType(relocType);
        ElfSymbol sym = (ElfSymbol) symbol.getNativeSymbol();
        int symno = sym.getIndex();
        int sectindex = reloc.getSection().getSectionId();
        int offset = reloc.getOffset();
        int addend = 0;

        switch (relocType) {
            case JAVA_CALL_DIRECT:
            case STUB_CALL_DIRECT:
            case FOREIGN_CALL_INDIRECT_GOT: {
                // Create relocation entry
                addend = -4; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                offset = offset + reloc.getSize() + addend;
                break;
            }
            case JAVA_CALL_INDIRECT:
            case METASPACE_GOT_REFERENCE:
            case EXTERNAL_PLT_TO_GOT: {
                addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to the move instruction.
                 * reloc.getOffset() points to the test instruction after the instruction that loads
                 * the address of polling page. So set the offset appropriately.
                 */
                offset = offset + addend;
                break;
            }
            case EXTERNAL_GOT_TO_PLT: {
                // this is load time relocations
                break;
            }
            default:
                throw new InternalError("Unhandled relocation type: " + relocType);
        }
        elfRelocTable.createRelocationEntry(sectindex, offset, symno, elfRelocType, addend);
    }

    private static int getELFRelocationType(RelocType relocType) {
        int elfRelocType = 0; // R_<ARCH>_NONE if #define'd to 0 for all values of ARCH
        switch (ElfTargetInfo.getElfArch()) {
            case Elf64_Ehdr.EM_X86_64:
                // Return R_X86_64_* entries based on relocType
                if (relocType == RelocType.JAVA_CALL_DIRECT ||
                                relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    elfRelocType = Elf64_Rela.R_X86_64_PLT32;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    elfRelocType = Elf64_Rela.R_X86_64_PC32;
                } else if (relocType == RelocType.JAVA_CALL_INDIRECT) {
                    elfRelocType = Elf64_Rela.R_X86_64_NONE;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE ||
                                relocType == RelocType.EXTERNAL_PLT_TO_GOT) {
                    elfRelocType = Elf64_Rela.R_X86_64_PC32;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT) {
                    elfRelocType = Elf64_Rela.R_X86_64_64;
                } else {
                    assert false : "Unhandled relocation type: " + relocType;
                }
                break;

            default:
                System.out.println("Relocation Type mapping: Unhandled architecture: " + ElfTargetInfo.getElfArch());
        }
        return elfRelocType;
    }
}
