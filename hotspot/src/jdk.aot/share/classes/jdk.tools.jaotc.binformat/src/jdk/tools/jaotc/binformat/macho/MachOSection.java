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
import java.nio.ByteOrder;

import jdk.tools.jaotc.binformat.macho.MachO;
import jdk.tools.jaotc.binformat.macho.MachO.section_64;
import jdk.tools.jaotc.binformat.macho.MachOByteBuffer;

public class MachOSection {
    ByteBuffer section;
    byte [] data;
    boolean hasrelocations;

    public MachOSection(String sectName, String segName, byte [] sectData, int sectFlags, boolean hasRelocations) {
        section = MachOByteBuffer.allocate(section_64.totalsize);

        // TODO: Hotspot uses long section names.
        //       They are getting truncated.
        //       Is this a problem??
        byte[] sectNameBytes = sectName.getBytes();
        int sectNameMax = section_64.sectname.sz < sectNameBytes.length ?
                         section_64.sectname.sz : sectNameBytes.length;

        for (int i = 0; i < sectNameMax; i++)
            section.put(section_64.sectname.off+i, sectNameBytes[i]);

        byte[] segNameBytes = segName.getBytes();
        int segNameMax = section_64.segname.sz < segNameBytes.length ?
                         section_64.segname.sz : segNameBytes.length;

        for (int i = 0; i < segNameMax; i++)
            section.put(section_64.segname.off+i, segNameBytes[i]);

        section.putLong(section_64.size.off, sectData.length);

        // For now use 8 byte alignment
        section.putInt(section_64.align.off, 3);

        section.putInt(section_64.flags.off, sectFlags);

        data = sectData;

        hasrelocations = hasRelocations;
    }

    public long getSize() {
        return section.getLong(section_64.size.off);
    }

    public int getAlign() {
        return (1 << section.getInt(section_64.align.off));
    }

    public byte[] getArray() {
        return section.array();
    }

    public byte[] getDataArray() {
        return data;
    }

    public void setAddr(long addr) {
        section.putLong(section_64.addr.off, addr);
    }

    public long getAddr() {
        return (section.getLong(section_64.addr.off));
    }

    public void setOffset(int offset) {
        section.putInt(section_64.offset.off, offset);
    }

    public int getOffset() {
        return (section.getInt(section_64.offset.off));
    }

    public void setReloff(int offset) {
        section.putInt(section_64.reloff.off, offset);
    }

    public void setRelcount(int count) {
        section.putInt(section_64.nreloc.off, count);
    }

    public boolean hasRelocations() {
        return hasrelocations;
    }
}


