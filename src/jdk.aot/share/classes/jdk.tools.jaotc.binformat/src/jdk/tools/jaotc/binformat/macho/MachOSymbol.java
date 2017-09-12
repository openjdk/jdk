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

package jdk.tools.jaotc.binformat.macho;

import java.nio.ByteBuffer;

import jdk.tools.jaotc.binformat.NativeSymbol;
import jdk.tools.jaotc.binformat.macho.MachO.nlist_64;
import jdk.tools.jaotc.binformat.macho.MachOByteBuffer;

final class MachOSymbol extends NativeSymbol {
    private final ByteBuffer sym;

    MachOSymbol(int symbolindex, int strindex, byte type, byte sectindex, long offset) {
        super(symbolindex);
        sym = MachOByteBuffer.allocate(nlist_64.totalsize);

        sym.putInt(nlist_64.n_strx.off, strindex);
        sym.put(nlist_64.n_type.off, type);
        // Section indexes start at 1 but we manage the index internally
        // as 0 relative
        sym.put(nlist_64.n_sect.off, (byte) (sectindex + 1));
        sym.putChar(nlist_64.n_desc.off, (char) 0);
        sym.putLong(nlist_64.n_value.off, offset);
    }

    byte[] getArray() {
        return sym.array();
    }
}
