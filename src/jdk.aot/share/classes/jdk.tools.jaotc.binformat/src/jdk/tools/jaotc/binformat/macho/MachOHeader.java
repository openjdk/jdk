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

import jdk.tools.jaotc.binformat.macho.MachO.mach_header_64;

final class MachOHeader {
    private final ByteBuffer header;

    MachOHeader() {
        header = MachOByteBuffer.allocate(mach_header_64.totalsize);

        header.putInt(mach_header_64.magic.off, mach_header_64.MH_MAGIC_64);
        header.putInt(mach_header_64.cputype.off, MachOTargetInfo.getMachOArch());
        header.putInt(mach_header_64.cpusubtype.off, MachOTargetInfo.getMachOSubArch());
        header.putInt(mach_header_64.flags.off, 0x2000);
        header.putInt(mach_header_64.filetype.off, mach_header_64.MH_OBJECT);
    }

    void setCmdSizes(int ncmds, int sizeofcmds) {
        header.putInt(mach_header_64.ncmds.off, ncmds);
        header.putInt(mach_header_64.sizeofcmds.off, sizeofcmds);
    }

    int getCmdSize() {
        return (header.getInt(mach_header_64.sizeofcmds.off));
    }

    byte[] getArray() {
        return header.array();
    }
}
