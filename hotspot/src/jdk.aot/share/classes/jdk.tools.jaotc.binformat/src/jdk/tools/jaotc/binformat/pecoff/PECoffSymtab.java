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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SYMBOL;
import jdk.tools.jaotc.binformat.pecoff.PECoffSymbol;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffSymtab {
    ArrayList<PECoffSymbol> symbols = new ArrayList<>();

    /**
     * number of symbols added
     */
    private int symbolCount;

    /**
     * String holding symbol table strings
     */
    private final StringBuilder strTabContent;

    /**
     * Keeps track of bytes in string table since strTabContent.length() is number of chars, not
     * bytes.
     */
    private int strTabNrOfBytes;

    /**
     * String holding Linker Directives
     */
    private final StringBuilder directives;

    PECoffSymtab() {
        symbolCount = 0;
        strTabContent = new StringBuilder();
        directives = new StringBuilder();

        // The first 4 bytes of the string table contain
        // the length of the table (including this length field).
        strTabNrOfBytes = 4;

        // Make room for the 4 byte length field
        strTabContent.append('\0').append('\0').append('\0').append('\0');

        // Linker Directives start with 3 spaces to signify ANSI
        directives.append("   ");
    }

    PECoffSymbol addSymbolEntry(String name, byte type, byte storageclass, byte secHdrIndex, long offset) {
        // Get the current symbol index and append symbol name to string table.
        int index;
        PECoffSymbol sym;

        if (name.isEmpty()) {
            index = 0;
            strTabContent.append('\0');
            strTabNrOfBytes += 1;
            sym = new PECoffSymbol(symbolCount, index, type, storageclass, secHdrIndex, offset);
            symbols.add(sym);
        } else {
            int nameSize = name.getBytes().length;

            // We can't trust strTabContent.length() since that is
            // chars (UTF16), keep track of bytes on our own.
            index = strTabNrOfBytes;
            // strTabContent.append('_').append(name).append('\0');
            strTabContent.append(name).append('\0');
            strTabNrOfBytes += (nameSize + 1);

            sym = new PECoffSymbol(symbolCount, index, type, storageclass, secHdrIndex, offset);
            symbols.add(sym);
            if (storageclass == IMAGE_SYMBOL.IMAGE_SYM_CLASS_EXTERNAL) {
                addDirective(name, type);
            }
        }
        symbolCount++;
        return (sym);
    }

    private void addDirective(String name, byte type) {
        directives.append("/EXPORT:" + name);
        if (type != IMAGE_SYMBOL.IMAGE_SYM_DTYPE_FUNCTION) {
            directives.append(",DATA");
        }
        directives.append(" ");
    }

    int getSymtabCount() {
        return symbolCount;
    }

    int getStrtabSize() {
        return strTabNrOfBytes;
    }

    // Return a byte array that contains the symbol table entries
    byte[] getSymtabArray() {
        ByteBuffer symtabData = PECoffByteBuffer.allocate(symbolCount * IMAGE_SYMBOL.totalsize);
        symtabData.order(ByteOrder.LITTLE_ENDIAN);

        // copy all symbols
        for (int i = 0; i < symbolCount; i++) {
            PECoffSymbol sym = symbols.get(i);
            byte[] arr = sym.getArray();
            symtabData.put(arr);
        }
        return (symtabData.array());
    }

    // Return the string table array
    byte[] getStrtabArray() {
        byte[] strs = strTabContent.toString().getBytes();

        // Update the size of the string table
        ByteBuffer buff = ByteBuffer.wrap(strs);
        buff.order(ByteOrder.LITTLE_ENDIAN);
        buff.putInt(0, strTabNrOfBytes);

        return (strs);
    }

    byte[] getDirectiveArray() {
        return (directives.toString().getBytes());
    }
}
