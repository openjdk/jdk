/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;

abstract class V9AlternateSpaceDecoder extends MemoryInstructionDecoder
                    implements V9InstructionDecoder {
    V9AlternateSpaceDecoder(int op3, String name, int dataType) {
        super(op3, name, dataType);
    }

    SPARCRegisterIndirectAddress newRegisterIndirectAddress(SPARCRegister rs1, SPARCRegister rs2) {
        return new SPARCV9RegisterIndirectAddress(rs1, rs2);
    }

    SPARCRegisterIndirectAddress newRegisterIndirectAddress(SPARCRegister rs1, int offset) {
        return new SPARCV9RegisterIndirectAddress(rs1, offset);
    }

    abstract Instruction decodeV9AsiLoadStore(int instruction,
                                              SPARCV9RegisterIndirectAddress addr,
                                              SPARCRegister rd,
                                              SPARCV9InstructionFactory factory);

    Instruction decodeMemoryInstruction(int instruction,
                                   SPARCRegisterIndirectAddress addr,
                                   SPARCRegister rd, SPARCInstructionFactory factory) {
        SPARCV9RegisterIndirectAddress v9addr = (SPARCV9RegisterIndirectAddress) addr;
        if (isIBitSet(instruction)) {
            // indirect asi
            v9addr.setIndirectAsi(true);
        } else {
            // immediate asi
            int asi = (instruction & ASI_MASK) >>> ASI_START_BIT;
            v9addr.setAddressSpace(asi);
        }
        return decodeV9AsiLoadStore(instruction, v9addr, rd,
                                    (SPARCV9InstructionFactory) factory);
    }
}
