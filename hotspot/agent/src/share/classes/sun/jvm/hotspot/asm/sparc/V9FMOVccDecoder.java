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
import sun.jvm.hotspot.utilities.Assert;

class V9FMOVccDecoder extends V9CMoveDecoder implements /* imports */ RTLDataTypes {
    private final int opf;
    private final int dataType;

    V9FMOVccDecoder(int opf, int dataType) {
        this.opf = opf;
        this.dataType = dataType;
    }

    private static String getFMoveCCName(int conditionCode, int conditionFlag, int dataType) {
        StringBuffer buf = new StringBuffer("fmov");
        buf.append(getFloatTypeCode(dataType));
        buf.append(getConditionName(conditionCode, conditionFlag));
        return buf.toString();
    }

    private static int getFMoveConditionFlag(int instruction) {
        return (instruction & OPF_CC_MASK) >>> OPF_CC_START_BIT;
    }

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCV9InstructionFactory v9factory = (SPARCV9InstructionFactory) factory;
        Instruction instr = null;
        int conditionFlag = getFMoveConditionFlag(instruction);
        if (conditionFlag == CFLAG_RESERVED1 || conditionFlag == CFLAG_RESERVED2) {
            instr = v9factory.newIllegalInstruction(instruction);
        } else {
            int rdNum = getDestinationRegister(instruction);
            int rs1Num = getSourceRegister1(instruction);
            SPARCRegister rd = RegisterDecoder.decode(dataType, rdNum);
            int conditionCode = getMoveConditionCode(instruction);
            SPARCRegister rs = RegisterDecoder.decode(dataType, rs1Num);
            String name = getFMoveCCName(conditionCode, conditionFlag, dataType);
            instr = v9factory.newV9FMOVccInstruction(name,opf, conditionCode, conditionFlag,
                               (SPARCFloatRegister)rs, (SPARCFloatRegister)rd);
        }

        return instr;
    }
}
