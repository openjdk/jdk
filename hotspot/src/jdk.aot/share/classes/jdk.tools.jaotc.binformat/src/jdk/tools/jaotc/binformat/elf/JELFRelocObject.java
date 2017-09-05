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

import java.io.IOException;
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

public class JELFRelocObject {

    private final BinaryContainer binContainer;

    private final ElfContainer elfContainer;

    private final int segmentSize;

    public JELFRelocObject(BinaryContainer binContainer, String outputFileName) {
        this.binContainer = binContainer;
        this.elfContainer = new ElfContainer(outputFileName);
        this.segmentSize = binContainer.getCodeSegmentSize();
    }

    private static ElfSection createByteSection(ArrayList<ElfSection> sections,
                                                String sectName,
                                                byte[] scnData,
                                                boolean hasRelocs,
                                                int align,
                                                int scnFlags,
                                                int scnType) {

        ElfSection sect = new ElfSection(sectName, scnData, scnFlags, scnType,
                                         hasRelocs, align, sections.size());
        // Add this section to our list
        sections.add(sect);

        return (sect);
    }

    private void createByteSection(ArrayList<ElfSection> sections,
                                   ByteContainer c, int scnFlags) {
        ElfSection sect;
        boolean hasRelocs = c.hasRelocations();
        byte[] scnData = c.getByteArray();

        int scnType = Elf64_Shdr.SHT_PROGBITS;
        boolean zeros = !hasRelocs;
        if (zeros) {
            for (byte b : scnData) {
                if (b != 0) {
                    zeros = false;
                    break;
                }
            }
            if (zeros) {
                scnType = Elf64_Shdr.SHT_NOBITS;
            }
        }

        sect = createByteSection(sections, c.getContainerName(),
                                 scnData, hasRelocs, segmentSize,
                                 scnFlags, scnType);
        c.setSectionId(sect.getSectionId());
    }

    private void createCodeSection(ArrayList<ElfSection> sections, CodeContainer c) {
        createByteSection(sections, c, Elf64_Shdr.SHF_ALLOC | Elf64_Shdr.SHF_EXECINSTR);
    }

    private void createReadOnlySection(ArrayList<ElfSection> sections, ReadOnlyDataContainer c) {
        createByteSection(sections, c, Elf64_Shdr.SHF_ALLOC);
    }

    private void createReadWriteSection(ArrayList<ElfSection> sections, ByteContainer c) {
        createByteSection(sections, c, Elf64_Shdr.SHF_ALLOC | Elf64_Shdr.SHF_WRITE);
    }

    /**
     * Create an ELF relocatable object
     *
     * @param relocationTable
     * @param symbols
     * @throws IOException throws {@code IOException} as a result of file system access failures.
     */
    public void createELFRelocObject(Map<Symbol, List<Relocation>> relocationTable, Collection<Symbol> symbols) throws IOException {
        // Allocate ELF Header
        ElfHeader eh = new ElfHeader();

        ArrayList<ElfSection> sections = new ArrayList<>();

        // Create the null section
        createByteSection(sections, null, null, false, 1, 0, 0);

        // Create text section
        createCodeSection(sections, binContainer.getCodeContainer());
        createReadOnlySection(sections, binContainer.getMetaspaceNamesContainer());
        createReadOnlySection(sections, binContainer.getKlassesOffsetsContainer());
        createReadOnlySection(sections, binContainer.getMethodsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getKlassesDependenciesContainer());
        createReadOnlySection(sections, binContainer.getMethodMetadataContainer());
        createReadOnlySection(sections, binContainer.getStubsOffsetsContainer());
        createReadOnlySection(sections, binContainer.getHeaderContainer().getContainer());
        createReadOnlySection(sections, binContainer.getCodeSegmentsContainer());
        createReadOnlySection(sections, binContainer.getConstantDataContainer());
        createReadOnlySection(sections, binContainer.getConfigContainer());
        createReadWriteSection(sections, binContainer.getKlassesGotContainer());
        createReadWriteSection(sections, binContainer.getCountersGotContainer());
        createReadWriteSection(sections, binContainer.getMetadataGotContainer());
        createReadWriteSection(sections, binContainer.getOopGotContainer());
        createReadWriteSection(sections, binContainer.getMethodStateContainer());
        createReadWriteSection(sections, binContainer.getExtLinkageGOTContainer());

        // Get ELF symbol data from BinaryContainer object's symbol tables
        ElfSymtab symtab = createELFSymbolTables(symbols);

        // Create string table section and symbol table sections in
        // that order since symtab section needs to set the index of
        // strtab in sh_link field
        ElfSection strTabSection = createByteSection(sections, ".strtab",
                                                     symtab.getStrtabArray(),
                                                     false, 1, 0,
                                                     Elf64_Shdr.SHT_STRTAB);

        // Now create .symtab section with the symtab data constructed.
        // On Linux, sh_link of symtab contains the index of string table
        // its symbols reference and sh_info contains the index of first
        // non-local symbol
        ElfSection symTabSection = createByteSection(sections, ".symtab",
                                                     symtab.getSymtabArray(),
                                                     false, 8, 0,
                                                     Elf64_Shdr.SHT_SYMTAB);
        symTabSection.setLink(strTabSection.getSectionId());
        symTabSection.setInfo(symtab.getNumLocalSyms());

        ElfRelocTable elfRelocTable = createElfRelocTable(sections, relocationTable);

        createElfRelocSections(sections, elfRelocTable, symTabSection.getSectionId());

        // Now, finally, after creating all sections, create shstrtab section
        ElfSection shStrTabSection = createByteSection(sections, ".shstrtab",
                                                       null, false, 1, 0,
                                                       Elf64_Shdr.SHT_STRTAB);
        eh.setSectionStrNdx(shStrTabSection.getSectionId());

        // Update all section offsets and the Elf header section offset
        // Write the Header followed by the contents of each section
        // and then the section structures (section table).
        int file_offset = Elf64_Ehdr.totalsize;

        // and round it up
        file_offset = (file_offset + (sections.get(1).getDataAlign() - 1)) &
                      ~((sections.get(1).getDataAlign() - 1));

        // Calc file offsets for section data skipping null section
        for (int i = 1; i < sections.size(); i++) {
            ElfSection sect = sections.get(i);
            file_offset = (file_offset + (sect.getDataAlign() - 1)) &
                          ~((sect.getDataAlign() - 1));
            sect.setOffset(file_offset);
            file_offset += sect.getSize();
        }

        // Align the section table
        file_offset = (file_offset + (ElfSection.getShdrAlign() - 1)) &
                      ~((ElfSection.getShdrAlign() - 1));

        // Update the Elf Header with the offset of the first Elf64_Shdr
        // and the number of sections.
        eh.setSectionOff(file_offset);
        eh.setSectionNum(sections.size());

        // Write out the Header
        elfContainer.writeBytes(eh.getArray());

        // Write out each section contents skipping null section
        for (int i = 1; i < sections.size(); i++) {
            ElfSection sect = sections.get(i);
            elfContainer.writeBytes(sect.getDataArray(), sect.getDataAlign());
        }

        // Write out the section table
        for (int i = 0; i < sections.size(); i++) {
            ElfSection sect = sections.get(i);
            elfContainer.writeBytes(sect.getArray(), ElfSection.getShdrAlign());
        }

        elfContainer.close();
    }

    /**
     * Construct ELF symbol data from BinaryContainer object's symbol tables. Both dynamic ELF symbol
     * table and ELF symbol table are created from BinaryContainer's symbol info.
     *
     * @param symbols
     */
    private static ElfSymtab createELFSymbolTables(Collection<Symbol> symbols) {
        ElfSymtab symtab = new ElfSymtab();

        // First, create the initial null symbol. This is a local symbol.
        symtab.addSymbolEntry("", (byte) 0, (byte) 0, Elf64_Shdr.SHN_UNDEF, 0, 0);

        // Now create ELF symbol entries for all symbols.
        for (Symbol symbol : symbols) {
            // Get the index of section this symbol is defined in.
            int secHdrIndex = symbol.getSection().getSectionId();
            ElfSymbol elfSymbol = symtab.addSymbolEntry(symbol.getName(), getELFTypeOf(symbol), getELFBindOf(symbol), (byte) secHdrIndex, symbol.getOffset(), symbol.getSize());
            symbol.setNativeSymbol(elfSymbol);
        }
        return (symtab);
    }

    private static byte getELFTypeOf(Symbol sym) {
        Kind kind = sym.getKind();
        if (kind == Symbol.Kind.NATIVE_FUNCTION || kind == Symbol.Kind.JAVA_FUNCTION) {
            return Elf64_Sym.STT_FUNC;
        } else if (kind == Symbol.Kind.OBJECT) {
            return Elf64_Sym.STT_OBJECT;
        }
        return Elf64_Sym.STT_NOTYPE;
    }

    private static byte getELFBindOf(Symbol sym) {
        Binding binding = sym.getBinding();
        if (binding == Symbol.Binding.GLOBAL) {
            return Elf64_Sym.STB_GLOBAL;
        }
        return Elf64_Sym.STB_LOCAL;
    }

    /**
     * Construct a Elf relocation table from BinaryContainer object's relocation tables.
     *
     * @param sections
     * @param relocationTable
     */
    private ElfRelocTable createElfRelocTable(ArrayList<ElfSection> sections,
                                              Map<Symbol, List<Relocation>> relocationTable) {

        ElfRelocTable elfRelocTable = new ElfRelocTable(sections.size());
        /*
         * For each of the symbols with associated relocation records, create a Elf relocation entry.
         */
        for (Map.Entry<Symbol, List<Relocation>> entry : relocationTable.entrySet()) {
            List<Relocation> relocs = entry.getValue();
            Symbol symbol = entry.getKey();

            for (Relocation reloc : relocs) {
                createRelocation(symbol, reloc, elfRelocTable);
            }
        }

        for (Map.Entry<Symbol, Relocation> entry : binContainer.getUniqueRelocationTable().entrySet()) {
            createRelocation(entry.getKey(), entry.getValue(), elfRelocTable);
        }

        return (elfRelocTable);
    }

    private static void createRelocation(Symbol symbol, Relocation reloc, ElfRelocTable elfRelocTable) {
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
                 * reloc.getOffset() points to the test instruction after the instruction that loads the address of
                 * polling page. So set the offset appropriately.
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
                System.out.println("Relocation Type mapping: Unhandled architecture");
        }
        return elfRelocType;
    }

    private static void createElfRelocSections(ArrayList<ElfSection> sections,
                                               ElfRelocTable elfRelocTable,
                                               int symtabsectidx) {

        // Grab count before we create new sections
        int count = sections.size();

        for (int i = 0; i < count; i++) {
            if (elfRelocTable.getNumRelocs(i) > 0) {
                ElfSection sect = sections.get(i);
                String relname = ".rela" + sect.getName();
                ElfSection relocSection = createByteSection(sections, relname,
                                                            elfRelocTable.getRelocData(i),
                                                            false, 8, 0, Elf64_Shdr.SHT_RELA);
                relocSection.setLink(symtabsectidx);
                relocSection.setInfo(sect.getSectionId());
            }
        }
    }
}
