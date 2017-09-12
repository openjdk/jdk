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

import jdk.tools.jaotc.binformat.macho.MachO.reloc_info;
import jdk.tools.jaotc.binformat.macho.MachOByteBuffer;

final class MachORelocEntry {
    private final ByteBuffer entry;

    MachORelocEntry(int offset, int symno, int pcrel, int length, int isextern, int type) {

        entry = MachOByteBuffer.allocate(reloc_info.totalsize);

        entry.putInt(reloc_info.r_address.off, offset);

        // Encode and store the relocation entry bitfields
        // @formatter:off
        entry.putInt(reloc_info.r_relocinfo.off,
            ((symno    & reloc_info.REL_SYMNUM_MASK) << reloc_info.REL_SYMNUM_SHIFT) |
            ((pcrel    & reloc_info.REL_PCREL_MASK)  << reloc_info.REL_PCREL_SHIFT)  |
            ((length   & reloc_info.REL_LENGTH_MASK) << reloc_info.REL_LENGTH_SHIFT) |
            ((isextern & reloc_info.REL_EXTERN_MASK) << reloc_info.REL_EXTERN_SHIFT) |
            ((type     & reloc_info.REL_TYPE_MASK)   << reloc_info.REL_TYPE_SHIFT));
        // @formatter:on
    }

    byte[] getArray() {
        return entry.array();
    }
}
