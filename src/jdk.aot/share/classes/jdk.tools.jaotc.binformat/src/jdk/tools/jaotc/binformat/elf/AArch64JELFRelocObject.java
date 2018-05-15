/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.CodeContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import jdk.tools.jaotc.binformat.elf.ElfSymbol;
import jdk.tools.jaotc.binformat.elf.ElfTargetInfo;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Ehdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Shdr;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Sym;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Rela;


public class AArch64JELFRelocObject extends JELFRelocObject {

    AArch64JELFRelocObject(BinaryContainer binContainer, String outputFileName) {
        super(binContainer, outputFileName);
    }

    void createRelocation(Symbol symbol, Relocation reloc, ElfRelocTable elfRelocTable) {
        RelocType relocType = reloc.getType();

        int elfRelocType = getELFRelocationType(relocType);
        ElfSymbol sym = (ElfSymbol) symbol.getNativeSymbol();
        int symno = sym.getIndex();
        int sectindex = reloc.getSection().getSectionId();
        int offset = reloc.getOffset();
        int addend = 0;

        switch (relocType) {
        case STUB_CALL_DIRECT:
        case JAVA_CALL_DIRECT: {
            break;
        }
        case EXTERNAL_PLT_TO_GOT:
            offset -= 16;
            elfRelocTable.createRelocationEntry(sectindex, offset, symno, Elf64_Rela.R_AARCH64_ADR_PREL_PG_HI21, addend);
            elfRelocTable.createRelocationEntry(sectindex, offset + 4, symno, Elf64_Rela.R_AARCH64_ADD_ABS_LO12_NC, addend);
            return;

        case FOREIGN_CALL_INDIRECT_GOT: {
            break;
        }
        case METASPACE_GOT_REFERENCE: {
            offset -= 4;

            elfRelocTable.createRelocationEntry(sectindex, offset, symno, Elf64_Rela.R_AARCH64_ADR_PREL_PG_HI21, addend);
            elfRelocTable.createRelocationEntry(sectindex, offset + 4, symno, Elf64_Rela.R_AARCH64_ADD_ABS_LO12_NC, addend);
            return;
        }
            // break;
        case JAVA_CALL_INDIRECT: {
            addend = -4;
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

    int getELFRelocationType(RelocType relocType) {
        int elfRelocType = 0; // R_<ARCH>_NONE if #define'd to 0 for all values of ARCH
        switch (ElfTargetInfo.getElfArch()) {
            case Elf64_Ehdr.EM_AARCH64:
                // Return R_X86_64_* entries based on relocType
                if (relocType == RelocType.JAVA_CALL_DIRECT ||
                    relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    elfRelocType = Elf64_Rela.R_AARCH64_CALL26;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    elfRelocType = Elf64_Rela.R_AARCH64_CALL26;
                } else if (relocType == RelocType.JAVA_CALL_INDIRECT) {
                    elfRelocType = Elf64_Rela.R_AARCH64_CALL26;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE ||
                           relocType == RelocType.EXTERNAL_PLT_TO_GOT) {
                    elfRelocType = Elf64_Rela.R_AARCH64_NONE;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT) {
                    elfRelocType = Elf64_Rela.R_AARCH64_ABS64;
                } else {
                    assert false : "Unhandled relocation type: " + relocType;
                }
                break;

            default:
                System.out.println("Relocation Type mapping: Unhandled architecture: "
                                   + ElfTargetInfo.getElfArch());
        }
        return elfRelocType;
    }
}
