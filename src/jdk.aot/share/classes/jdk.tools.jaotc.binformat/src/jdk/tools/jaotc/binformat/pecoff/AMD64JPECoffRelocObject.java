/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_FILE_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_RELOCATION;

public class AMD64JPECoffRelocObject extends JPECoffRelocObject {

    AMD64JPECoffRelocObject(BinaryContainer binContainer, String outputFileName) {
        super (binContainer, outputFileName);
    }

    @Override
    protected void createRelocation(Symbol symbol, Relocation reloc, PECoffRelocTable pecoffRelocTable) {
        RelocType relocType = reloc.getType();

        int pecoffRelocType = getPECoffRelocationType(relocType);
        PECoffSymbol sym = (PECoffSymbol) symbol.getNativeSymbol();
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
            case JAVA_CALL_INDIRECT: {
                // Do nothing.
                return;
            }
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
        pecoffRelocTable.createRelocationEntry(sectindex, offset, symno, pecoffRelocType);
    }

    // Return IMAGE_RELOCATION Type based on relocType
    private static int getPECoffRelocationType(RelocType relocType) {
        int pecoffRelocType = 0; // R_<ARCH>_NONE if #define'd to 0 for all values of ARCH
        switch (PECoffTargetInfo.getPECoffArch()) {
            case IMAGE_FILE_HEADER.IMAGE_FILE_MACHINE_AMD64:
                if (relocType == RelocType.JAVA_CALL_DIRECT ||
                                relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.JAVA_CALL_INDIRECT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_ABSOLUTE;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE ||
                                relocType == RelocType.EXTERNAL_PLT_TO_GOT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_ADDR64;
                } else {
                    assert false : "Unhandled relocation type: " + relocType;
                }
                break;
            default:
                System.out.println("Relocation Type mapping: Unhandled architecture");
        }
        return pecoffRelocType;
    }
}
