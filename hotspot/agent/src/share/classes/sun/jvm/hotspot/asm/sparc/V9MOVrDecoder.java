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

class V9MOVrDecoder extends V9CMoveDecoder {
    private static final String regConditionNames[] = {
        null, "movrz", "movrlez", "movrlz", null, "movrnz", "movrgz", "movrgez"
    };

    private static String getMOVrName(int conditionCode) {
        return regConditionNames[conditionCode];
    }

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCV9InstructionFactory v9factory = (SPARCV9InstructionFactory) factory;
        Instruction instr = null;
        int regConditionCode = getRegisterConditionCode(instruction);
        String name = getMOVrName(regConditionCode);
        if (name == null) {
            instr = v9factory.newIllegalInstruction(instruction);
        } else {
            int rdNum = getDestinationRegister(instruction);
            SPARCRegister rd = SPARCRegisters.getRegister(rdNum);
            SPARCRegister rs1 = SPARCRegisters.getRegister(getSourceRegister1(instruction));
            ImmediateOrRegister operand2 = getCMoveSource(instruction, 10);
            instr = v9factory.newV9MOVrInstruction(name, rs1, operand2, rd, regConditionCode);
        }

        return instr;
    }
}
