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

import jdk.tools.jaotc.binformat.NativeSymbol;
import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_SYMBOL;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffSymbol extends NativeSymbol {
    private final ByteBuffer sym;

    PECoffSymbol(int symbolindex, int strindex, byte type, byte storageclass, byte sectindex, long offset) {
        super(symbolindex);
        sym = PECoffByteBuffer.allocate(IMAGE_SYMBOL.totalsize);

        // We don't use short names
        sym.putInt(IMAGE_SYMBOL.Short.off, 0);

        sym.putInt(IMAGE_SYMBOL.Long.off, strindex);
        sym.putInt(IMAGE_SYMBOL.Value.off, (int) offset);

        // Section indexes start at 1 but we manage the index internally
        // as 0 relative except in this structure
        sym.putChar(IMAGE_SYMBOL.SectionNumber.off, (char) (sectindex + 1));

        sym.putChar(IMAGE_SYMBOL.Type.off, (char) type);
        sym.put(IMAGE_SYMBOL.StorageClass.off, storageclass);
        sym.put(IMAGE_SYMBOL.NumberOfAuxSymbols.off, (byte) 0);
    }

    byte[] getArray() {
        return sym.array();
    }
}
