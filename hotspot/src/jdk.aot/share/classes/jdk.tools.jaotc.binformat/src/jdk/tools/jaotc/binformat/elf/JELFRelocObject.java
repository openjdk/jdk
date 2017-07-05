/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import jdk.tools.jaotc.jnilibelf.ELFContainer;
import jdk.tools.jaotc.jnilibelf.ELFSymbol;
import jdk.tools.jaotc.jnilibelf.JNIELFContainer;
import jdk.tools.jaotc.jnilibelf.JNIELFRelocation;
import jdk.tools.jaotc.jnilibelf.JNIELFTargetInfo;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.ELF;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF.Elf_Cmd;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF.Elf_Type;
import jdk.tools.jaotc.jnilibelf.Pointer;

public class JELFRelocObject {

    private final BinaryContainer binContainer;

    private final JNIELFContainer elfContainer;

    private final int segmentSize;

    public JELFRelocObject(BinaryContainer binContainer, String outputFileName, String aotVersion) {
        this.binContainer = binContainer;
        this.elfContainer = new JNIELFContainer(outputFileName, aotVersion);
        this.segmentSize = binContainer.getCodeSegmentSize();
    }

    private void createByteSection(ByteContainer c, int scnFlags) {
        byte[] scnData = c.getByteArray();
        int scnType = ELF.SHT_PROGBITS;
        boolean zeros = !c.hasRelocations();
        if (zeros) {
            for (byte b : scnData) {
                if (b != 0) {
                    zeros = false;
                    break;
                }
            }
            if (zeros) {
                scnType = ELF.SHT_NOBITS;
            }
        }

        int sectionId = elfContainer.createSection(c.getContainerName(), scnData, Elf_Type.ELF_T_BYTE, segmentSize, scnType, scnFlags, ELF.SHN_UNDEF, 0);
        c.setSectionId(sectionId);
        // Clear out code section data to allow for GC
        c.clear();
    }

    private void createCodeSection(CodeContainer c) {
        createByteSection(c, ELF.SHF_ALLOC | ELF.SHF_EXECINSTR);
    }

    private void createReadOnlySection(ReadOnlyDataContainer c) {
        createByteSection(c, ELF.SHF_ALLOC);
    }

    private void createReadWriteSection(ByteContainer c) {
        createByteSection(c, ELF.SHF_ALLOC | ELF.SHF_WRITE);
    }

    /**
     * Create an ELF relocatable object using jdk.tools.jaotc.jnilibelf API.
     *
     * @param relocationTable
     * @param symbols
     * @throws IOException throws {@code IOException} as a result of file system access failures.
     */
    public void createELFRelocObject(Map<Symbol, List<Relocation>> relocationTable, Collection<Symbol> symbols) throws IOException {
        // Allocate ELF Header
        elfContainer.createELFHeader(ELF.ET_REL);

        // Create text section
        createCodeSection(binContainer.getCodeContainer());
        createReadOnlySection(binContainer.getMetaspaceNamesContainer());
        createReadOnlySection(binContainer.getKlassesOffsetsContainer());
        createReadOnlySection(binContainer.getMethodsOffsetsContainer());
        createReadOnlySection(binContainer.getKlassesDependenciesContainer());
        createReadWriteSection(binContainer.getMetaspaceGotContainer());
        createReadWriteSection(binContainer.getMetadataGotContainer());
        createReadWriteSection(binContainer.getMethodStateContainer());
        createReadWriteSection(binContainer.getOopGotContainer());
        createReadWriteSection(binContainer.getMethodMetadataContainer());
        createReadOnlySection(binContainer.getStubsOffsetsContainer());
        createReadOnlySection(binContainer.getHeaderContainer().getContainer());
        createReadOnlySection(binContainer.getCodeSegmentsContainer());
        createReadOnlySection(binContainer.getConstantDataContainer());
        createReadOnlySection(binContainer.getConfigContainer());

        // createExternalLinkage();

        createCodeSection(binContainer.getExtLinkageContainer());
        createReadWriteSection(binContainer.getExtLinkageGOTContainer());

        // Get ELF symbol data from BinaryContainer object's symbol tables
        createELFSymbolTables(symbols);

        // Create string table section and symbol table sections in
        // that order since symtab section needs to set the index of strtab in sh_link field
        int strTabSectionIndex = elfContainer.createSection(".strtab", elfContainer.getStrTabContent().getBytes(StandardCharsets.UTF_8), Elf_Type.ELF_T_BYTE, 1, ELF.SHT_STRTAB, 0, ELF.SHN_UNDEF, 0);

        // Now create .symtab section with the symtab data constructed. On Linux, sh_link of symtab
        // contains the index of string table its symbols reference and
        // sh_info contains the index of first non-local symbol
        int scnInfo = elfContainer.getFirstNonLocalSymbolIndex();
        int symTabSectionIndex = elfContainer.createSection(".symtab", getELFSymbolTableData(), Elf_Type.ELF_T_SYM, 8, ELF.SHT_SYMTAB, ELF.SHF_ALLOC, strTabSectionIndex, scnInfo);

        buildRelocations(relocationTable, symTabSectionIndex);

        // Now, finally, after creating all sections, create shstrtab section
        elfContainer.createSection(".shstrtab", elfContainer.getShStrTabContent().getBytes(StandardCharsets.UTF_8), Elf_Type.ELF_T_BYTE, 1, ELF.SHT_STRTAB, 0, ELF.SHN_UNDEF, 0);

        // Run elf_update
        elfContainer.elfUpdate(Elf_Cmd.ELF_C_NULL);

        // Run elfUpdate again to write it out.
        elfContainer.elfUpdate(Elf_Cmd.ELF_C_WRITE);
        // Finish ELF processing
        elfContainer.elfEnd();
    }

    private void buildRelocations(Map<Symbol, List<Relocation>> relocationTable, final int symTabSectionIndex) {
        /*
         * Create relocation sections. This needs to be done after symbol table sections were
         * created since relocation entries will need indices of sections to which they apply.
         */
        createELFRelocationTables(relocationTable);
        createAllRelocationSections(new SymTabELFContainer(symTabSectionIndex));
    }

    /**
     * Construct ELF symbol data from BinaryContainer object's symbol tables. Both dynamic ELF
     * symbol table and ELF symbol table are created from BinaryContainer's symbol info.
     *
     * @param symbols
     */
    private void createELFSymbolTables(Collection<Symbol> symbols) {
        // First, create the initial null symbol. This is a local symbol.
        elfContainer.createELFSymbolEntry("", 0, 0, ELF.SHN_UNDEF, 0, 0, true);

        // Now create ELF symbol entries for all symbols.
        for (Symbol symbol : symbols) {
            // Get the index of section this symbol is defined in.
            int secHdrIndex = symbol.getSection().getSectionId();
            boolean isLocal = (symbol.getBinding() == Binding.LOCAL);
            ELFSymbol elfSymbol = elfContainer.createELFSymbolEntry(symbol.getName(), getELFTypeOf(symbol), getELFBindOf(symbol), secHdrIndex, symbol.getSize(), symbol.getOffset(), isLocal);
            symbol.setElfSymbol(elfSymbol);
        }
    }

    /**
     * Construct ELF symbol data from BinaryContainer object's symbol tables.
     *
     * @return a byte array containing the symbol table
     */
    private byte[] getELFSymbolTableData() {
        final int entrySize = JNIELFTargetInfo.sizeOfSymtabEntry();

        // First, add all local symbols.
        List<ELFSymbol> localSymbols = elfContainer.getLocalSymbols();
        List<ELFSymbol> globalSymbols = elfContainer.getGlobalSymbols();

        int localSymCount = localSymbols.size();
        int globalSymCount = globalSymbols.size();
        byte[] sectionDataArray = new byte[(localSymCount + globalSymCount) * entrySize];

        for (int i = 0; i < localSymCount; i++) {
            ELFSymbol symbol = localSymbols.get(i);
            Pointer address = symbol.getAddress();
            address.copyBytesTo(sectionDataArray, entrySize, i * entrySize);
        }

        // Next, add all global symbols.

        for (int i = 0; i < globalSymCount; i++) {
            ELFSymbol symbol = globalSymbols.get(i);
            Pointer address = symbol.getAddress();
            address.copyBytesTo(sectionDataArray, entrySize, (localSymCount + i) * entrySize);
        }

        return sectionDataArray;
    }

    private static int getELFTypeOf(Symbol sym) {
        Kind kind = sym.getKind();
        if (kind == Symbol.Kind.NATIVE_FUNCTION || kind == Symbol.Kind.JAVA_FUNCTION) {
            return ELF.STT_FUNC;
        } else if (kind == Symbol.Kind.OBJECT) {
            return ELF.STT_OBJECT;
        }
        return ELF.STT_NOTYPE;
    }

    private static int getELFBindOf(Symbol sym) {
        Binding binding = sym.getBinding();
        if (binding == Symbol.Binding.GLOBAL) {
            return ELF.STB_GLOBAL;
        }
        return ELF.STB_LOCAL;
    }

    /**
     * Construct ELF relocation section data from BinaryContainer object's relocation tables.
     *
     * @param relocationTable
     */
    private void createELFRelocationTables(Map<Symbol, List<Relocation>> relocationTable) {
        /*
         * For each of the symbols with associated relocation records, create an ELF relocation
         * entry.
         */
        for (Map.Entry<Symbol, List<Relocation>> entry : relocationTable.entrySet()) {
            List<Relocation> relocs = entry.getValue();
            Symbol symbol = entry.getKey();

            for (Relocation reloc : relocs) {
                createRelocation(symbol, reloc);
            }
        }

        for (Map.Entry<Symbol, Relocation> entry : binContainer.getUniqueRelocationTable().entrySet()) {
            createRelocation(entry.getKey(), entry.getValue());
        }
    }

    private void createRelocation(Symbol symbol, Relocation reloc) {
        RelocType relocType = reloc.getType();
        int elfRelocType = getELFRelocationType(relocType);

        switch (relocType) {
            case FOREIGN_CALL_DIRECT:
            case JAVA_CALL_DIRECT:
            case STUB_CALL_DIRECT:
            case FOREIGN_CALL_INDIRECT_GOT: {
                // Create relocation entry
                int addend = -4; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                int offset = reloc.getOffset() + reloc.getSize() + addend;
                elfContainer.createELFRelocationEntry(reloc.getSection(), offset, elfRelocType, addend, symbol.getElfSymbol());
                break;
            }
            case FOREIGN_CALL_DIRECT_FAR: {
                // Create relocation entry
                int addend = -8; // Size in bytes of the patch location
                // Relocation should be applied at the location after call operand
                // 10 = 2 (jmp [r]) + 8 (imm64)
                int offset = reloc.getOffset() + reloc.getSize() + addend - 2;
                elfContainer.createELFRelocationEntry(reloc.getSection(), offset, elfRelocType, addend, symbol.getElfSymbol());
                break;
            }
            case FOREIGN_CALL_INDIRECT:
            case JAVA_CALL_INDIRECT:
            case STUB_CALL_INDIRECT: {
                // Do nothing.
                break;
            }
            case EXTERNAL_DATA_REFERENCE_FAR: {
                // Create relocation entry
                int addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to the move instruction.
                 * reloc.getOffset() points to the test instruction after the instruction that loads
                 * the address of polling page. So set the offset appropriately.
                 */
                int offset = reloc.getOffset() + addend;
                elfContainer.createELFRelocationEntry(reloc.getSection(), offset, elfRelocType, addend, symbol.getElfSymbol());
                break;
            }
            case METASPACE_GOT_REFERENCE:
            case EXTERNAL_PLT_TO_GOT:
            case STATIC_STUB_TO_STATIC_METHOD:
            case STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT: {
                int addend = -4; // Size of 32-bit address of the GOT
                /*
                 * Relocation should be applied before the test instruction to the move instruction.
                 * reloc.getOffset() points to the test instruction after the instruction that loads
                 * the address of polling page. So set the offset appropriately.
                 */
                int offset = reloc.getOffset() + addend;
                elfContainer.createELFRelocationEntry(reloc.getSection(), offset, elfRelocType, addend, symbol.getElfSymbol());
                break;
            }
            case EXTERNAL_GOT_TO_PLT:
            case LOADTIME_ADDRESS: {
                // this is load time relocations
                elfContainer.createELFRelocationEntry(reloc.getSection(), reloc.getOffset(), elfRelocType, 0, symbol.getElfSymbol());
                break;
            }
            default:
                throw new InternalError("Unhandled relocation type: " + relocType);
        }
    }

    // TODO: Populate the mapping of RelocType to ELF relocation types
    private static int getELFRelocationType(RelocType relocType) {
        int elfRelocType = 0; // R_<ARCH>_NONE if #define'd to 0 for all values of ARCH
        switch (JNIELFTargetInfo.getELFArch()) {
            case ELF.EM_X64_64:
                // Return R_X86_64_* entries based on relocType
                if (relocType == RelocType.FOREIGN_CALL_DIRECT || relocType == RelocType.JAVA_CALL_DIRECT || relocType == RelocType.FOREIGN_CALL_INDIRECT_GOT) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_PLT32;
                } else if (relocType == RelocType.STUB_CALL_DIRECT) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_PC32;
                } else if (relocType == RelocType.FOREIGN_CALL_DIRECT_FAR) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_64;
                } else if (relocType == RelocType.FOREIGN_CALL_INDIRECT || relocType == RelocType.JAVA_CALL_INDIRECT || relocType == RelocType.STUB_CALL_INDIRECT) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_NONE;
                } else if ((relocType == RelocType.EXTERNAL_DATA_REFERENCE_FAR)) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_GOTPCREL;
                } else if (relocType == RelocType.METASPACE_GOT_REFERENCE || relocType == RelocType.EXTERNAL_PLT_TO_GOT || relocType == RelocType.STATIC_STUB_TO_STATIC_METHOD ||
                                relocType == RelocType.STATIC_STUB_TO_HOTSPOT_LINKAGE_GOT) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_PC32;
                } else if (relocType == RelocType.EXTERNAL_GOT_TO_PLT || relocType == RelocType.LOADTIME_ADDRESS) {
                    elfRelocType = JNIELFRelocation.X86_64.R_X86_64_64;
                } else {
                    assert false : "Unhandled relocation type: " + relocType;
                }
                break;
            default:
                System.out.println("Relocation Type mapping: Unhandled architecture");
        }
        return elfRelocType;
    }

    private void createAllRelocationSections(ELFContainer symtab) {
        for (Map.Entry<ELFContainer, ArrayList<Pointer>> entry : elfContainer.getRelocTables().entrySet()) {
            createRelocationSection(entry.getKey(), entry.getValue(), symtab);
        }
    }

    private void createRelocationSection(ELFContainer container, ArrayList<Pointer> relocations, ELFContainer symtab) {
        String secName = container.getContainerName();
        int entrySize = JNIELFTargetInfo.sizeOfRelocEntry();
        int numEntries = relocations.size();
        byte[] sectionDataBytes = new byte[numEntries * entrySize];

        for (int index = 0; index < relocations.size(); index++) {
            Pointer entry = relocations.get(index);
            entry.copyBytesTo(sectionDataBytes, entrySize, index * entrySize);
        }
        String fullSecName;
        // If relocDat is non-null create section
        if (sectionDataBytes.length > 0) {
            int scnType;
            Elf_Type dataType;
            if (JNIELFTargetInfo.createReloca() == 0) {
                scnType = ELF.SHT_REL;
                dataType = Elf_Type.ELF_T_REL;
                fullSecName = ".rel" + secName;
            } else {
                scnType = ELF.SHT_RELA;
                dataType = Elf_Type.ELF_T_RELA;
                fullSecName = ".rela" + secName;
            }
            // assert compareBytes(relocData.toByteArray(), sectionDataBytes) : "******* Bad array
            // copy";
            // sh_link holds the index of section header of symbol table associated with this
            // relocation table.
            // sh_info holds the index of section header to which this relocation table applies
            // to.
            elfContainer.createSection(fullSecName, sectionDataBytes, dataType, 8, scnType, ELF.SHF_ALLOC, symtab.getSectionId(), container.getSectionId());
        }
    }

    private static class SymTabELFContainer implements ELFContainer {
        private final int symTabSectionIndex;

        public SymTabELFContainer(int symTabSectionIndex) {
            this.symTabSectionIndex = symTabSectionIndex;
        }

        @Override
        public String getContainerName() {
            return ".symtab";
        }

        @Override
        public int getSectionId() {
            return symTabSectionIndex;
        }
    }
}
