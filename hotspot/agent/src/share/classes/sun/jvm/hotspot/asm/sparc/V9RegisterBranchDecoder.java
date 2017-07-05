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

abstract class V9RegisterBranchDecoder extends V9BranchDecoder {
    static int getDisp16(int instruction) {
        int offset = (DISP_16_LO_MASK & instruction)  |
           ((DISP_16_HI_MASK & instruction) >>> (DISP_16_HI_START_BIT - DISP_16_LO_NUMBITS));

        // sign extend and word align
        offset = extractSignedIntFromNBits(offset, 16);
        offset <<= 2;

        return offset;
    }

    String getConditionName(int conditionCode, boolean isAnnuled) {
        return null;
    }

    abstract String getRegisterConditionName(int rcond);

    public Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCV9InstructionFactory v9factory = (SPARCV9InstructionFactory) factory;
        int rcond = (BRANCH_RCOND_MASK & instruction) >>> BRANCH_RCOND_START_BIT;
        if (rcond == BRANCH_RCOND_RESERVED1 || rcond == BRANCH_RCOND_RESERVED2)
            return factory.newIllegalInstruction(instruction);

        SPARCRegister rs1 = SPARCRegisters.getRegister(getSourceRegister1(instruction));
        boolean predictTaken = getPredictTaken(instruction);
        boolean annuled = getAnnuledBit(instruction);
        PCRelativeAddress addr = new PCRelativeAddress(getDisp16(instruction));
        String name = getRegisterConditionName(rcond);
        return v9factory.newV9RegisterBranchInstruction(name, addr, annuled, rcond, rs1, predictTaken);
    }
}
