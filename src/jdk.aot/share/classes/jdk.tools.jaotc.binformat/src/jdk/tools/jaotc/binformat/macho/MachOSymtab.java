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



package jdk.tools.jaotc.binformat.macho;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import jdk.tools.jaotc.binformat.macho.MachO.nlist_64;
import jdk.tools.jaotc.binformat.macho.MachO.symtab_command;

final class MachOSymtab {

    /**
     * ByteBuffer holding the LC_SYMTAB command contents.
     */
    private final ByteBuffer symtabCmd;

    private int symtabDataSize;

    private final ArrayList<MachOSymbol> localSymbols = new ArrayList<>();
    private final ArrayList<MachOSymbol> globalSymbols = new ArrayList<>();
    private final ArrayList<MachOSymbol> undefSymbols = new ArrayList<>();

    /**
     * Number of symbols added.
     */
    private int symbolCount;

    /**
     * String holding symbol table strings.
     */
    private final StringBuilder strTabContent = new StringBuilder();

    /**
     * Keeps track of bytes in string table since strTabContent.length() is number of chars, not
     * bytes.
     */
    private int strTabNrOfBytes = 0;

    MachOSymtab() {
        symtabCmd = MachOByteBuffer.allocate(symtab_command.totalsize);

        symtabCmd.putInt(symtab_command.cmd.off, symtab_command.LC_SYMTAB);
        symtabCmd.putInt(symtab_command.cmdsize.off, symtab_command.totalsize);

        symbolCount = 0;

    }

    static int getAlign() {
        return (4);
    }

    MachOSymbol addSymbolEntry(String name, byte type, byte secHdrIndex, long offset) {
        // Get the current symbol index and append symbol name to string table.
        int index;
        MachOSymbol sym;

        if (name.isEmpty()) {
            index = 0;
            strTabContent.append('\0');
            strTabNrOfBytes += 1;
            sym = new MachOSymbol(symbolCount, index, type, secHdrIndex, offset);
            localSymbols.add(sym);
        } else {
            // We can't trust strTabContent.length() since that is
            // chars (UTF16), keep track of bytes on our own.
            index = strTabNrOfBytes;
            strTabContent.append("_").append(name).append('\0');
            // + 1 for null, + 1 for "_"
            strTabNrOfBytes += (name.getBytes().length + 1 + 1);

            sym = new MachOSymbol(symbolCount, index, type, secHdrIndex, offset);
            switch (type) {
                case nlist_64.N_EXT:
                    undefSymbols.add(sym);
                    break;
                case nlist_64.N_SECT:
                case nlist_64.N_UNDF:  // null symbol
                    localSymbols.add(sym);
                    break;
                case nlist_64.N_SECT | nlist_64.N_EXT:
                    globalSymbols.add(sym);
                    break;
                default:
                    System.out.println("Unsupported Symbol type " + type);
                    break;
            }
        }
        symbolCount++;
        return (sym);
    }

    void setOffset(int symoff) {
        symtabCmd.putInt(symtab_command.symoff.off, symoff);
    }

    // Update the symbol indexes once all symbols have been added.
    // This is required since we'll be reordering the symbols in the
    // file to be in the order of Local, global and Undefined.
    void updateIndexes() {
        int index = 0;

        // Update the local symbol indexes
        for (int i = 0; i < localSymbols.size(); i++) {
            MachOSymbol sym = localSymbols.get(i);
            sym.setIndex(index++);
        }

        // Update the global symbol indexes
        for (int i = 0; i < globalSymbols.size(); i++) {
            MachOSymbol sym = globalSymbols.get(i);
            sym.setIndex(index++);
        }

        // Update the undefined symbol indexes
        for (int i = index; i < undefSymbols.size(); i++) {
            MachOSymbol sym = undefSymbols.get(i);
            sym.setIndex(index++);
        }
    }

    // Update LC_SYMTAB command fields based on the number of symbols added
    // return the file size taken up by symbol table entries and strings
    int calcSizes() {
        int stroff;

        stroff = symtabCmd.getInt(symtab_command.symoff.off) + (nlist_64.totalsize * symbolCount);
        symtabCmd.putInt(symtab_command.nsyms.off, symbolCount);
        symtabCmd.putInt(symtab_command.stroff.off, stroff);
        symtabCmd.putInt(symtab_command.strsize.off, strTabNrOfBytes);
        symtabDataSize = (nlist_64.totalsize * symbolCount) + strTabNrOfBytes;

        return (symtabDataSize);
    }

    int getNumLocalSyms() {
        return localSymbols.size();
    }

    int getNumGlobalSyms() {
        return globalSymbols.size();
    }

    int getNumUndefSyms() {
        return undefSymbols.size();
    }

    byte[] getCmdArray() {
        return symtabCmd.array();
    }

    // Create a single byte array that contains the symbol table entries
    // and string table
    byte[] getDataArray() {
        ByteBuffer symtabData = MachOByteBuffer.allocate(symtabDataSize);
        byte[] retarray;

        // Add the local symbols
        for (int i = 0; i < localSymbols.size(); i++) {
            MachOSymbol sym = localSymbols.get(i);
            byte[] arr = sym.getArray();
            symtabData.put(arr);
        }
        // Add the global symbols
        for (int i = 0; i < globalSymbols.size(); i++) {
            MachOSymbol sym = globalSymbols.get(i);
            byte[] arr = sym.getArray();
            symtabData.put(arr);
        }
        // Add the undefined symbols
        for (int i = 0; i < undefSymbols.size(); i++) {
            MachOSymbol sym = undefSymbols.get(i);
            byte[] arr = sym.getArray();
            symtabData.put(arr);
        }

        // Add the stringtable
        byte[] strs = strTabContent.toString().getBytes();
        symtabData.put(strs);

        retarray = symtabData.array();

        return (retarray);
    }
}
