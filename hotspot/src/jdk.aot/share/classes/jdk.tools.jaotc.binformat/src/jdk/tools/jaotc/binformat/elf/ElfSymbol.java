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

package jdk.tools.jaotc.binformat.elf;

import java.nio.ByteBuffer;

import jdk.tools.jaotc.binformat.NativeSymbol;
import jdk.tools.jaotc.binformat.elf.Elf.Elf64_Sym;
import jdk.tools.jaotc.binformat.elf.ElfByteBuffer;

final class ElfSymbol extends NativeSymbol {
    private final ByteBuffer sym;

    ElfSymbol(int symbolindex, int strindex, byte type, byte bind, byte sectindex, long offset, long size) {
        super(symbolindex);
        sym = ElfByteBuffer.allocate(Elf64_Sym.totalsize);

        sym.putInt(Elf64_Sym.st_name.off, strindex);
        sym.put(Elf64_Sym.st_info.off, Elf64_Sym.ELF64_ST_INFO(bind, type));
        sym.put(Elf64_Sym.st_other.off, (byte) 0);
        // Section indexes start at 1 but we manage the index internally
        // as 0 relative
        sym.putChar(Elf64_Sym.st_shndx.off, (char) (sectindex));
        sym.putLong(Elf64_Sym.st_value.off, offset);
        sym.putLong(Elf64_Sym.st_size.off, size);
    }

    byte[] getArray() {
        return sym.array();
    }
}
