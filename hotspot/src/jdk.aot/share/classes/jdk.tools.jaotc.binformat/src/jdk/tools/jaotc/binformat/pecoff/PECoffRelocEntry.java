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

import jdk.tools.jaotc.binformat.pecoff.PECoff.IMAGE_RELOCATION;
import jdk.tools.jaotc.binformat.pecoff.PECoffByteBuffer;

final class PECoffRelocEntry {
    private final ByteBuffer entry;

    PECoffRelocEntry(int offset, int symno, int type) {

        entry = PECoffByteBuffer.allocate(IMAGE_RELOCATION.totalsize);

        entry.putInt(IMAGE_RELOCATION.VirtualAddress.off, offset);
        entry.putInt(IMAGE_RELOCATION.SymbolTableIndex.off, symno);
        entry.putChar(IMAGE_RELOCATION.Type.off, (char) type);
    }

    byte[] getArray() {
        return entry.array();
    }
}
