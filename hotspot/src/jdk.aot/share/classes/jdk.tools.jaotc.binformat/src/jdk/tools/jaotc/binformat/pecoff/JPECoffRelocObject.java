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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jdk.tools.jaotc.binformat.Container;
import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.CodeContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.binformat.Relocation;
import jdk.tools.jaotc.binformat.Relocation.RelocType;
import jdk.tools.jaotc.binformat.Symbol;
import jdk.tools.jaotc.binformat.NativeSymbol;
import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;

import jdk.tools.jaotc.binformat.pecoff.PECoff;
import jdk.tools.jaotc.binformat.pecoff.PECoffSymbol;
import jdk.tools.jaotc.binformat.pecoff.PECoffTargetInfo;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_FILE_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SECTION_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SYMBOL;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_RELOCATION;

public class JPECoffRelocObject {

    private final BinaryContainer binContainer;

    private final PECoffContainer pecoffContainer;

    private final int segmentSize;

    public JPECoffRelocObject(BinaryContainer binContainer, String outputFileName, String aotVersion) {
        this.binContainer = binContainer;
        this.pecoffContainer = new PECoffContainer(outputFileName, aotVersion);
        this.segmentSize = binContainer.getCodeSegmentSize();
    }

    private PECoffSection createByteSection(ArrayList<PECoffSection>sections,
                                         String sectName,
                                         byte [] scnData,
                                         boolean hasRelocs,
                                         int scnFlags) {

        PECoffSection sect = new PECoffSection(sectName,
                                         scnData,
                                         scnFlags,
                                         hasRelocs,
                                         sections.size());
        // Add this section to our list
        sections.add(sect);

        return (sect);
    }

    private void createByteSection(ArrayList<PECoffSection>sections,
                                   ByteContainer c, int scnFlags) {
        PECoffSection sect;
        boolean hasRelocs = c.hasRelocations();
        byte[] scnData = c.getByteArray();

        sect = createByteSection(sections, c.getContainerName(),
                                 scnData, hasRelocs,
                                 scnFlags);

        c.setSectionId(sect.getSectionId());
    }

    private void createCodeSection(ArrayList<PECoffSection>sections, CodeContainer c) {
        createByteSection(sections, c, IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_READ |
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_EXECUTE |
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_16BYTES |
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_CODE);
    }

    private void createReadOnlySection(ArrayList<PECoffSection>sections, ReadOnlyDataContainer c) {
        createByteSection(sections, c, IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_READ |
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_16BYTES |
                                       IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_INITIALIZED_DATA);
    }

    private void createReadWriteSection(ArrayList<PECoffSection>sections, ByteContainer c) {
        int scnFlags = IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_READ |
                       IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_WRITE |
                       IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_8BYTES;

        if (c.getByteArray().length > 0)
            scnFlags |= IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_INITIALIZED_DATA;
        else
            scnFlags |= IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_UNINITIALIZED_DATA;

        createByteSection(sections, c, scnFlags);
    }

    /**
     * Create an PECoff relocatable object
     *
     * @param relocationTable
     * @param symbols
     * @throws IOException throws {@code IOException} as a result of file system access failures.
     */
    public void createPECoffRelocObject(Map<Symbol, List<Relocation>> relocationTable, Collection<Symbol> symbols) throws IOException {
        ArrayList<PECoffSection> sections = new ArrayList<PECoffSection>();

        // Create text section
        createCodeSection(sections, binContainer.getCodeContainer());
        createReadOnlySection(sections, binContainer.getMetaspaceNamesContainer());
        createReadOnlySection(sections, binContainer.getKlassesOffsetsContainer());
        createReadOnlySection(sections, binContainer.getMethodsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getKlassesDependenciesContainer());
        createReadWriteSection(sections, binContainer.getMetaspaceGotContainer());
        createReadWriteSection(sections, binContainer.getMetadataGotContainer());
        createReadWriteSection(sections, binContainer.getMethodStateContainer());
        createReadWriteSection(sections, binContainer.getOopGotContainer());
        createReadWriteSection(sections, binContainer.getMethodMetadataContainer());
        createReadOnlySection(sections, binContainer.getStubsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getHeaderContainer().getContainer());
        createReadOnlySection(sections, binContainer.getCodeSegmentsContainer());
        createReadOnlySection(sections, binContainer.getConstantDataContainer());
        createReadOnlySection(sections, binContainer.getConfigContainer());

        // createExternalLinkage();

        createCodeSection(sections, binContainer.getExtLinkageContainer());
        createReadWriteSection(sections, binContainer.getExtLinkageGOTContainer());

        // Allocate PECoff Header
        PECoffHeader header = new PECoffHeader();

        // Get PECoff symbol data from BinaryContainer object's symbol tables
        PECoffSymtab symtab = createPECoffSymbolTables(sections, symbols);

        // Add Linker Directives Section
        createByteSection(sections, ".drectve",
                          symtab.getDirectiveArray(), false,
                          IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_INFO |
                          IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_REMOVE |
                          IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_1BYTES);

        // Create the Relocation Tables
        PECoffRelocTable pecoffRelocs = createPECoffRelocTable(sections, relocationTable);

        // File Output Order
        //
        //   HEADER           (Need address of Symbol Table + symbol count)
        //   SECTIONS         (Need pointer to Section Data, Relocation Table)
        //   DIRECTIVES
        //   SYMBOL TABLE
        //   SYMBOLS
        //   SECTION DATA
        //   RELOCATION TABLE

        // Calculate Offset for Symbol table
        int file_offset = IMAGE_FILE_HEADER.totalsize +
                          (IMAGE_SECTION_HEADER.totalsize*sections.size());

        // Update Header fields
        header.setSectionCount(sections.size());
        header.setSymbolCount(symtab.getSymtabCount());
        header.setSymbolOff(file_offset);

        // Calculate file offset for first section
        file_offset += ((symtab.getSymtabCount() * IMAGE_SYMBOL.totalsize) +
                        symtab.getStrtabSize());
        // And round it up
        file_offset = (file_offset + (sections.get(0).getDataAlign()-1)) &
                      ~((sections.get(0).getDataAlign()-1));

        // Calc file offsets for section data
        for (int i = 0; i < sections.size(); i++) {
            PECoffSection sect = sections.get(i);
            file_offset = (file_offset + (sect.getDataAlign()-1)) &
                           ~((sect.getDataAlign()-1));
            sect.setOffset(file_offset);
            file_offset += sect.getSize();
        }

        // Update relocation sizing information in each section
        for (int i = 0; i < sections.size(); i++) {
            PECoffSection sect = sections.get(i);
            if (sect.hasRelocations()) {
                int nreloc = pecoffRelocs.getNumRelocs(i);
                sect.setReloff(file_offset);
                sect.setRelcount(nreloc);
                // extended relocations add an addition entry
                if (nreloc > 0xFFFF) nreloc++;
                file_offset += (nreloc * IMAGE_RELOCATION.totalsize);
            }
        }

        // Write out the Header
        pecoffContainer.writeBytes(header.getArray());

        // Write out the section table
        for (int i = 0; i < sections.size(); i++) {
            PECoffSection sect = sections.get(i);
            pecoffContainer.writeBytes(sect.getArray(), PECoffSection.getShdrAlign());
        }

        // Write out the symbol table and string table
        pecoffContainer.writeBytes(symtab.getSymtabArray(), 4);
        pecoffContainer.writeBytes(symtab.getStrtabArray(), 1);

        // Write out each section contents
        for (int i = 0; i < sections.size(); i++) {
            PECoffSection sect = sections.get(i);
            pecoffContainer.writeBytes(sect.getDataArray(), sect.getDataAlign());
        }

        // Write out Relocation Tables
        for (int i = 0; i < sections.size(); i++) {
            if (pecoffRelocs.getNumRelocs(i) > 0) {
                pecoffContainer.writeBytes(pecoffRelocs.getRelocData(i));
            }
        }
        pecoffContainer.close();
    }

    /**
     * Construct PECoff symbol data from BinaryContainer object's symbol tables. Both dynamic PECoff
     * symbol table and PECoff symbol table are created from BinaryContainer's symbol info.
     *
     * @param symbols
     */
    private PECoffSymtab createPECoffSymbolTables(ArrayList<PECoffSection> sections, Collection<Symbol> symbols) {
        PECoffSymtab symtab = new PECoffSymtab();

        // First, create the initial null symbol. This is a local symbol.
        // symtab.addSymbolEntry("", (byte)0, (byte)0, (byte)0, 0, 0);

        // Now create PECoff symbol entries for all symbols.
        for (Symbol symbol : symbols) {
            // Get the index of section this symbol is defined in.
            int secHdrIndex = symbol.getSection().getSectionId();
            PECoffSymbol pecoffSymbol = symtab.addSymbolEntry(symbol.getName(), getPECoffTypeOf(symbol), getPECoffClassOf(symbol), (byte)secHdrIndex, symbol.getOffset(), symbol.getSize());
            symbol.setNativeSymbol((NativeSymbol)pecoffSymbol);
        }
        return (symtab);
    }

    private static byte getPECoffTypeOf(Symbol sym) {
        Kind kind = sym.getKind();
        if (kind == Symbol.Kind.NATIVE_FUNCTION || kind == Symbol.Kind.JAVA_FUNCTION) {
            return IMAGE_SYMBOL.IMAGE_SYM_DTYPE_FUNCTION;
        }
        return IMAGE_SYMBOL.IMAGE_SYM_DTYPE_NONE;
    }

    private static byte getPECoffClassOf(Symbol sym) {
        Binding binding = sym.getBinding();
        if (binding == Symbol.Binding.GLOBAL) {
            return IMAGE_SYMBOL.IMAGE_SYM_CLASS_EXTERNAL;
        }
        return IMAGE_SYMBOL.IMAGE_SYM_CLASS_STATIC;
    }

    /**
     * Construct a PECoff relocation table from BinaryContainer object's relocation tables.
     *
     * @param sections
     * @param relocationTable
     */
    private PECoffRelocTable createPECoffRelocTable(ArrayList<PECoffSection> sections,
                                              Map<Symbol, List<Relocation>> relocationTable) {

        PECoffRelocTable pecoffRelocTable = new PECoffRelocTable(sections.size());
        /*
         * For each of the symbols with associated relocation records, create a PECoff relocation
         * entry.
         */
        for (Map.Entry<Symbol, List<Relocation>> entry : relocationTable.entrySet()) {
            List<Relocation> relocs = entry.getValue();
            Symbol symbol = entry.getKey();

            for (Relocation reloc : relocs) {
                createRelocation(symbol, reloc, pecoffRelocTable);
            }
        }

        for (Map.Entry<Symbol, Relocation> entry : binContainer.getUniqueRelocationTable().entrySet()) {
            createRelocation(entry.getKey(), entry.getValue(), pecoffRelocTable);
        }

        return (pecoffRelocTable);
    }

    private void createRelocation(Symbol symbol, Relocation reloc, PECoffRelocTable pecoffRelocTable) {
        RelocType relocType = reloc.getType();

        int pecoffRelocType = getPECoffRelocationType(relocType);
        PECoffSymbol sym = (PECoffSymbol)symbol.getNativeSymbol();
        int symno = sym.getIndex();
        int sectindex = reloc.getSection().getSectionId();
        int offset = reloc.getOffset();
        int addend = 0;

        switch (relocType) {
            case FOREIGN_CALL_DIRECT:
            case JAVA_CALL_DIRECT:
            case STUB_CALL_DIRECT:
            case FOREIGN_CALL_INDIRECT_GOT: {
                // Create relocation entry
                addend = -4; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                offset = offset + reloc.getSize() + addend;
                break;
            }
            case FOREIGN_CALL_DIRECT_FAR: {
                // Create relocation entry
                addend = -8; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                // 10 = 2 (jmp [r]) + 8 (imm64)
                offset = offset + reloc.getSize() + addend - 2;
                break;
            }
            case FOREIGN_CALL_INDIRECT:
            case JAVA_CALL_INDIRECT:
            case STUB_CALL_INDIRECT: {
                // Do nothing.
                return;
            }
            case EXTERNAL_DATA_REFERENCE_FAR: {
                // Create relocation entry
                addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to the move instruction.
                 * offset points to the test instruction after the instruction that loads
                 * the address of polling page. So set the offset appropriately.
                 */
                offset = offset + addend;
                break;
            }
            case METASPACE_GOT_REFERENCE:
            case EXTERNAL_PLT_TO_GOT:
            case STATIC_STUB_TO_STATIC_METHOD:
            case STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT: {
                addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to
                 * the move instruction. reloc.getOffset() points to the
                 * test instruction after the instruction that loads the
                 * address of polling page. So set the offset appropriately.
                 */
                offset = offset + addend;
                break;
            }
            case EXTERNAL_GOT_TO_PLT:
            case LOADTIME_ADDRESS: {
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
                if (relocType == RelocType.FOREIGN_CALL_DIRECT ||
                    relocType == RelocType.JAVA_CALL_DIRECT ||
                    relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.FOREIGN_CALL_DIRECT_FAR) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_ADDR64;
                } else if (relocType == RelocType.FOREIGN_CALL_INDIRECT ||
                           relocType == RelocType.JAVA_CALL_INDIRECT ||
                           relocType == RelocType.STUB_CALL_INDIRECT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_ABSOLUTE;
                } else if ((relocType == RelocType.EXTERNAL_DATA_REFERENCE_FAR)) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE ||
                           relocType == RelocType.EXTERNAL_PLT_TO_GOT ||
                           relocType == RelocType.STATIC_STUB_TO_STATIC_METHOD ||
                           relocType == RelocType.STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT) {
                    pecoffRelocType = IMAGE_RELOCATION.IMAGE_REL_AMD64_REL32;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT ||
                           relocType == RelocType.LOADTIME_ADDRESS) {
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
