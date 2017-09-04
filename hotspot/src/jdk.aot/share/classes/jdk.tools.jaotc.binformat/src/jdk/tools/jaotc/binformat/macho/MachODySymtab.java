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

import jdk.tools.jaotc.binformat.macho.MachO.dysymtab_command;
import jdk.tools.jaotc.binformat.macho.MachOByteBuffer;

final class MachODySymtab {
    private final ByteBuffer dysymtab;

    MachODySymtab(int nlocal, int nglobal, int nundef) {
        dysymtab = MachOByteBuffer.allocate(dysymtab_command.totalsize);

        dysymtab.putInt(dysymtab_command.cmd.off, dysymtab_command.LC_DYSYMTAB);
        dysymtab.putInt(dysymtab_command.cmdsize.off, dysymtab_command.totalsize);
        dysymtab.putInt(dysymtab_command.ilocalsym.off, 0);
        dysymtab.putInt(dysymtab_command.nlocalsym.off, nlocal);
        dysymtab.putInt(dysymtab_command.iextdefsym.off, nlocal);
        dysymtab.putInt(dysymtab_command.nextdefsym.off, nglobal);
        dysymtab.putInt(dysymtab_command.iundefsym.off, nlocal + nglobal);
        dysymtab.putInt(dysymtab_command.nundefsym.off, nundef);
    }

    byte[] getArray() {
        return dysymtab.array();
    }
}
