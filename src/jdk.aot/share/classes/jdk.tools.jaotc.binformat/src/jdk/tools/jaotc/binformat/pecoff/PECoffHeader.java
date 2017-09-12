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

import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_FILE_HEADER;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffHeader {
    private final ByteBuffer header;

    PECoffHeader() {
        header = PECoffByteBuffer.allocate(IMAGE_FILE_HEADER.totalsize);

        header.putChar(IMAGE_FILE_HEADER.Machine.off, IMAGE_FILE_HEADER.IMAGE_FILE_MACHINE_AMD64);
        header.putInt(IMAGE_FILE_HEADER.TimeDateStamp.off, (int) (System.currentTimeMillis() / 1000));
        header.putInt(IMAGE_FILE_HEADER.PointerToSymbolTable.off, 0);
        header.putInt(IMAGE_FILE_HEADER.NumberOfSymbols.off, 0);
        header.putChar(IMAGE_FILE_HEADER.SizeOfOptionalHeader.off, (char) 0);
        header.putChar(IMAGE_FILE_HEADER.Characteristics.off, (char) 0);

    }

    // Update header with the number of total sections
    void setSectionCount(int count) {
        header.putChar(IMAGE_FILE_HEADER.NumberOfSections.off, (char) count);
    }

    // Update header with the number of total symbols
    void setSymbolCount(int count) {
        header.putInt(IMAGE_FILE_HEADER.NumberOfSymbols.off, count);
    }

    // Update header with the offset of symbol table
    void setSymbolOff(int offset) {
        header.putInt(IMAGE_FILE_HEADER.PointerToSymbolTable.off, offset);
    }

    byte[] getArray() {
        return header.array();
    }
}
