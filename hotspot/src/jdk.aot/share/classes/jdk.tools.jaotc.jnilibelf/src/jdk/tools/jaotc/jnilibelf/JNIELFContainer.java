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

package jdk.tools.jaotc.jnilibelf;

import static jdk.tools.jaotc.jnilibelf.UnsafeAccess.UNSAFE;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.ELF;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF;
import jdk.tools.jaotc.jnilibelf.JNILibELFAPI.LibELF.Elf_Type;

/**
 * A class abstraction of an ELF file.
 *
 */
public class JNIELFContainer {

    private String outputFileName;
    private File outFile;
    private int outFileDesc;

    /**
     * Pointer to Elf file. This is the same as struct Elf found in libelf.h
     */
    private Pointer elfPtr;

    /**
     * Class of the ELF container - one of ELFCLASS32 or ELFCLASS64.
     */
    private final int elfClass;

    /**
     * Pointer to ELF Header.
     */
    private Pointer ehdrPtr;

    /**
     * Pointer to Program Header.
     */
    private Pointer phdrPtr;

    /**
     * String holding .shstrtab contents.
     */
    private String shStrTabContent = "";

    /**
     * Map of local symbol indexes to ELF symbol entries.
     */
    private List<ELFSymbol> localSymbolIndex = new ArrayList<>();

    /**
     * Map of global symbol indexes to ELF symbol entries.
     */
    private List<ELFSymbol> globalSymbolIndex = new ArrayList<>();

    /**
     * String holding .strtab contents.
     */
    private StringBuilder strTabContent = new StringBuilder();

    /**
     * Keeps track of nr of bytes in .strtab since strTabContent.length() is number of chars, not
     * bytes.
     */
    private int strTabNrOfBytes = 0;

    /**
     * A hashtable that holds (section-name, relocation-table) pairs. For example, [(".rela.text",
     * rela-text-reloc-entries), (".rela.plt", rela-plt-reloc-entries), ...].
     */
    private Map<ELFContainer, ArrayList<Pointer>> relocTables = new HashMap<>();

    /**
     * Create reloca; 0 => false and non-zero => true.
     */
    private final int createReloca;

    /**
     * Construct an ELFContainer in preparation for a disk image with file {@code prefix}.
     *
     * @param fileName name of ELF file to be created
     */
    public JNIELFContainer(String fileName, String aotVersion) {
        // Check for version compatibility
        if (!JNILibELFAPI.elfshim_version().equals(aotVersion)) {
            throw new InternalError("libelfshim version mismatch: " + JNILibELFAPI.elfshim_version() + " vs " + aotVersion);
        }

        elfClass = JNIELFTargetInfo.getELFClass();
        createReloca = JNIELFTargetInfo.createReloca();
        outputFileName = fileName;
    }

    /**
     * Get the local ELF symbol table.
     *
     * @return local symbol table
     */
    public List<ELFSymbol> getLocalSymbols() {
        return localSymbolIndex;
    }

    /**
     * Get the global ELF symbol table.
     *
     * @return list of global ELF symbol table entries
     */
    public List<ELFSymbol> getGlobalSymbols() {
        return globalSymbolIndex;
    }

    /**
     * Get string table content (.strtab).
     *
     * @return string table content
     */
    public String getStrTabContent() {
        return strTabContent.toString();
    }

    /**
     * Get section header string table content (.shstrtab).
     *
     * @return section header string table content
     */
    public String getShStrTabContent() {
        return shStrTabContent;
    }

    /**
     * Get relocation tables.
     *
     * @return relocation tables
     */
    public Map<ELFContainer, ArrayList<Pointer>> getRelocTables() {
        return relocTables;
    }

    /**
     * Get the index of first non-local symbol in symbol table.
     *
     * @return symbol table index
     */
    public int getFirstNonLocalSymbolIndex() {
        return localSymbolIndex.size();
    }

    /**
     * Create ELF header of type {@code ececType}.
     *
     * @param type type of ELF executable
     */
    public void createELFHeader(int type) {
        // Check for version compatibility
        if (JNILibELFAPI.elf_version(ELF.EV_CURRENT) == ELF.EV_NONE) {
            throw new InternalError("ELF version mismatch");
        }

        outFile = constructRelocFile(outputFileName);
        // Open a temporary file for the shared library to be created
        // TODO: Revisit file permissions; need to add execute permission
        outFileDesc = JNILibELFAPI.open_rw(outFile.getPath());

        if (outFileDesc == -1) {
            System.out.println("Failed to open file " + outFile.getPath() + " to write relocatable object.");
        }

        elfPtr = JNILibELFAPI.elf_begin(outFileDesc, LibELF.Elf_Cmd.ELF_C_WRITE.intValue(), new Pointer(0L));
        if (elfPtr == null) {
            throw new InternalError("elf_begin failed");
        }

        // Allocate new Ehdr of current architecture class

        ehdrPtr = JNILibELFAPI.gelf_newehdr(elfPtr, elfClass);

        JNILibELFAPI.ehdr_set_data_encoding(ehdrPtr, JNIELFTargetInfo.getELFEndian());
        JNILibELFAPI.set_Ehdr_e_machine(elfClass, ehdrPtr, JNIELFTargetInfo.getELFArch());
        JNILibELFAPI.set_Ehdr_e_type(elfClass, ehdrPtr, type);
        JNILibELFAPI.set_Ehdr_e_version(elfClass, ehdrPtr, ELF.EV_CURRENT);
    }

    /**
     * If the file name has a .so extension, replace it with .o extension. Else just add .o
     * extension
     *
     * @param fileName
     * @return File object
     */
    private static File constructRelocFile(String fileName) {
        File relocFile = new File(fileName);
        if (relocFile.exists()) {
            if (!relocFile.delete()) {
                throw new InternalError("Failed to delete existing " + fileName + " file");
            }
        }
        return relocFile;
    }

    /**
     * Create {@code count} number of Program headers.
     *
     * @param count number of program headers to create
     * @return true upon success; false upon failure
     */
    public boolean createProgramHeader(int count) {
        phdrPtr = JNILibELFAPI.gelf_newphdr(elfPtr, count);
        if (phdrPtr == null) {
            System.out.println("gelf_newphdr error");
            return false;
        }
        return true;
    }

    /**
     * Set program header to be of type self.
     *
     * @return true
     */
    public boolean setProgHdrTypeToSelf() {
        // Set program header to be of type self
        JNILibELFAPI.phdr_set_type_self(elfClass, ehdrPtr, phdrPtr);
        // And thus mark it as dirty so that elfUpdate can recompute the structures
        JNILibELFAPI.elf_flagphdr(elfPtr, LibELF.Elf_Cmd.ELF_C_SET.intValue(), LibELF.ELF_F_DIRTY);
        // TODO: Error checking; look at the return value of elf_update
        // and call elf_errmsg appropriately.
        return true;
    }

    /**
     * Create a section. The corresponding section header and section data are created by calling
     * the necessary libelf APIs. The section that is created is inserted into the ELF container.
     *
     * @param secName name of the section
     * @param scnData section data
     * @param dataType data type
     * @param align section alignment
     * @param scnType section type
     * @param scnFlags section flags
     * @param scnLink sh_link field of Elf{32,64}_Shdr
     * @param scnInfo sh_info field of Elf{32,64}_Shdr
     * @return section index
     */
    public int createSection(String secName, byte[] scnData, Elf_Type dataType, int align, int scnType, int scnFlags, int scnLink, int scnInfo) {
        // Create a new section
        Pointer scnPtr = JNILibELFAPI.elf_newscn(elfPtr);
        if (scnPtr == null) {
            throw new InternalError("elf_newscn error");
        }

        // Allocate section data for the section
        Pointer scnDataPtr = JNILibELFAPI.elf_newdata(scnPtr);
        if (scnDataPtr == null) {
            String errMsg = JNILibELFAPI.elf_errmsg(-1);
            throw new InternalError("elf_newdata error: " + errMsg);
        }

        // Get the pointer to section header associated with the new section
        Pointer scnHdrPtr = JNILibELFAPI.elf64_getshdr(scnPtr);

        // Add name of the section to section name string
        // If secName is null, point the name to the 0th index
        // that holds `\0'
        byte[] modScnData;
        if (secName.isEmpty()) {
            JNILibELFAPI.set_Shdr_sh_name(elfClass, scnHdrPtr, 0);
            modScnData = scnData;
        } else {
            if (secName.equals(".shstrtab")) {
                // Modify .shstrtab data by inserting '\0' at index 0
                String shstrtabSecName = ".shstrtab" + '\0';
                // Additional byte for the '\0' at position 0
                ByteBuffer nbuf = ByteBuffer.allocate(scnData.length + 1 + shstrtabSecName.length());
                nbuf.put(0, (byte) 0);
                nbuf.position(1);
                nbuf.put(scnData);
                nbuf.position(scnData.length + 1);
                // Add the section name ".shstrtab" to its own data
                nbuf.put(shstrtabSecName.getBytes(StandardCharsets.UTF_8));
                modScnData = nbuf.array();
                JNILibELFAPI.set_Shdr_sh_name(elfClass, scnHdrPtr, scnData.length + 1);
                // Set strtab section index
                JNILibELFAPI.set_Ehdr_e_shstrndx(elfClass, ehdrPtr, JNILibELFAPI.elf_ndxscn(scnPtr));
            } else if (secName.equals(".strtab")) {
                // Modify strtab section data to insert '\0' at position 0.
                // Additional byte for the '\0' at position 0
                ByteBuffer nbuf = ByteBuffer.allocate(scnData.length + 1);
                nbuf.put(0, (byte) 0);
                nbuf.position(1);
                nbuf.put(scnData);
                modScnData = nbuf.array();
                // Set the sh_name
                JNILibELFAPI.set_Shdr_sh_name(elfClass, scnHdrPtr, shStrTabContent.length() + 1);
                // Add scnName to stringList
                shStrTabContent += secName + '\0';
            } else {
                // Set the sh_name
                JNILibELFAPI.set_Shdr_sh_name(elfClass, scnHdrPtr, shStrTabContent.length() + 1);
                // Add scnName to stringList
                shStrTabContent += secName + '\0';
                modScnData = scnData;
            }
        }

        final int scnDataBufSize = modScnData.length;

        Pointer scnDataBufPtr = null;
        if (scnType != ELF.SHT_NOBITS) {
        // Allocate native memory for section data
          final long address = UNSAFE.allocateMemory(scnDataBufSize + 1);
          scnDataBufPtr = new Pointer(address);
          scnDataBufPtr.put(modScnData);
        } else {
          scnDataBufPtr = new Pointer(0L);
        }

        // Set data descriptor fields
        JNILibELFAPI.set_Data_d_align(scnDataPtr, align);
        JNILibELFAPI.set_Data_d_buf(scnDataPtr, scnDataBufPtr);
        JNILibELFAPI.set_Data_d_size(scnDataPtr, scnDataBufSize);
        JNILibELFAPI.set_Data_d_off(scnDataPtr, 0);
        JNILibELFAPI.set_Data_d_type(scnDataPtr, dataType.intValue());
        JNILibELFAPI.set_Data_d_version(scnDataPtr, ELF.EV_CURRENT);

        JNILibELFAPI.set_Shdr_sh_type(elfClass, scnHdrPtr, scnType);
        JNILibELFAPI.set_Shdr_sh_flags(elfClass, scnHdrPtr, scnFlags);
        JNILibELFAPI.set_Shdr_sh_entsize(elfClass, scnHdrPtr, 0); // TODO: Is this right??
        JNILibELFAPI.set_Shdr_sh_link(elfClass, scnHdrPtr, scnLink);
        JNILibELFAPI.set_Shdr_sh_info(elfClass, scnHdrPtr, scnInfo);

        // Add hash section to section pointer list
        int index = JNILibELFAPI.elf_ndxscn(scnPtr);
        return index;
    }

    /**
     * Create an ELF symbol entry for a symbol with the given properties.
     *
     * @param name name of the section in which symName is referenced
     * @param type type of symName
     * @param bind binding of symName
     * @param secHdrIndex section header index of the section in which symName is referenced
     *            (st_shndx of ELF symbol entry)
     * @param size symName size (st_size of ELF symbol entry)
     * @param value symName value (st_value of ELF symbol entry)
     * @param isLocal true if symbol is local.
     */
    public ELFSymbol createELFSymbolEntry(String name, int type, int bind, int secHdrIndex, int size, int value, boolean isLocal) {
        // Get the current symbol index and append symbol name to string table.
        int index;
        if (name.isEmpty()) {
            index = 0;
        } else {
            // NOTE: The +1 comes from the null symbol!
            // We can't trust strTabContent.length() since that is chars (UTF16), keep track of
            // bytes on our own.
            index = strTabNrOfBytes + 1;
            strTabContent.append(name).append('\0');
            strTabNrOfBytes += name.getBytes(StandardCharsets.UTF_8).length + 1;
        }

        // Create ELF symbol entry
        long address = JNILibELFAPI.create_sym_entry(elfClass, index, type, bind, secHdrIndex, size, value);
        if (address == 0) {
            throw new InternalError("create_sym_entry failed");
        }
        Pointer ptr = new Pointer(address);

        if (isLocal) {
            final int localIndex = localSymbolIndex.size();
            ELFSymbol symbol = new ELFSymbol(name, localIndex, ptr, isLocal);
            localSymbolIndex.add(symbol);
            return symbol;
        } else {
            final int globalIndex = globalSymbolIndex.size();
            ELFSymbol symbol = new ELFSymbol(name, globalIndex, ptr, isLocal);
            globalSymbolIndex.add(symbol);
            return symbol;
        }
    }

    /**
     * Create an ELF relocation entry for given symbol {@code name} to section {@code secname}.
     *
     * @param container the section
     * @param offset offset into the section contents at which the relocation needs to be applied
     * @param type ELF type of the relocation entry
     * @param addend Addend for for relocation of type reloca
     */
    public void createELFRelocationEntry(ELFContainer container, int offset, int type, int addend, ELFSymbol elfSymbol) {
        // Get the index of the symbol.
        int index;
        if (elfSymbol.isLocal()) {
            index = elfSymbol.getIndex();
        } else {
            /*
             * For global symbol entries the index will be offset by the number of local symbols
             * which will be listed first in the symbol table.
             */
            index = elfSymbol.getIndex() + localSymbolIndex.size();
        }

        long address = JNILibELFAPI.create_reloc_entry(elfClass, offset, index, type, addend, createReloca);
        if (address == 0) {
            throw new InternalError("create_reloc_entry failed");
        }
        Pointer ptr = new Pointer(address);
        /*
         * If section name associated with this symbol is set to undefined i.e., secname is null,
         * symIndex is undef i.e., 0.
         */
        if (relocTables.get(container) == null) {
            // Allocate a new table and add it to the hash table of reloc tables
            relocTables.put(container, new ArrayList<>());
        }

        // Add the entry
        relocTables.get(container).add(ptr);
    }

    /**
     * Invokes native libelf function loff_t elf_update (Elf *elfPtr, Elf_Cmd cmd).
     *
     * @param cmd command
     * @return return value of the native function called
     */
    public boolean elfUpdate(LibELF.Elf_Cmd cmd) {
        JNILibELFAPI.elf_update(elfPtr, cmd.intValue());
        // TODO: Error checking; look at the return value of elf_update
        // and call elf_errmsg appropriately.
        return true;
    }

    /**
     * Wrapper function that invokes int elf_end (Elf *elfPtr). and closes ELF output file
     * descriptor
     *
     * @return true
     */
    public boolean elfEnd() {
        // Finish ELF processing
        JNILibELFAPI.elf_end(elfPtr);
        // Close file descriptor
        JNILibELFAPI.close(outFileDesc);
        return true;
    }
}
