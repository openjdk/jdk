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

abstract class MemoryInstructionDecoder extends InstructionDecoder {
    final int    op3;
    final String name;
    final int    dataType;

    SPARCRegisterIndirectAddress newRegisterIndirectAddress(SPARCRegister rs1, SPARCRegister rs2) {
        return new SPARCRegisterIndirectAddress(rs1, rs2);
    }

    SPARCRegisterIndirectAddress newRegisterIndirectAddress(SPARCRegister rs1, int offset) {
        return new SPARCRegisterIndirectAddress(rs1, offset);
    }

    static void setAddressSpace(int instruction, SPARCRegisterIndirectAddress addr) {
        int asi = (instruction & ASI_MASK) >>> ASI_START_BIT;
        addr.setAddressSpace(asi);
    }

    SPARCRegisterIndirectAddress getRegisterIndirectAddress(int instruction) {
        SPARCRegister rs1 = SPARCRegisters.getRegister(getSourceRegister1(instruction));
        boolean iBit = isIBitSet(instruction);
        SPARCRegisterIndirectAddress addr = null;
        if (iBit) {
            int simm13 = extractSignedIntFromNBits(instruction, 13);
            addr = newRegisterIndirectAddress(rs1,simm13);
        } else {
            SPARCRegister rs2 = SPARCRegisters.getRegister(getSourceRegister2(instruction));
            addr = newRegisterIndirectAddress(rs1,rs2);
        }
        return addr;
    }

    MemoryInstructionDecoder(int op3, String name, int dataType) {
        this.op3 = op3;
        this.name = name;
        this.dataType = dataType;
    }

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCRegisterIndirectAddress addr = getRegisterIndirectAddress(instruction);
        SPARCRegister rd = getDestination(instruction);
        boolean isV9Okay = (factory instanceof SPARCV9InstructionFactory);
        if ( (rd == null) || (! isV9Okay && rd.isV9Only()) )
            return factory.newIllegalInstruction(instruction);

        return decodeMemoryInstruction(instruction, addr, rd, factory);
    }

    SPARCRegister getDestination(int instruction) {
        int rdNum = getDestinationRegister(instruction);
        SPARCRegister rd = RegisterDecoder.decode(dataType, rdNum);
        return rd;
    }

    abstract Instruction decodeMemoryInstruction(int instruction,
                                   SPARCRegisterIndirectAddress addr,
                                   SPARCRegister rd, SPARCInstructionFactory factory);
}
