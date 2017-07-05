/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.asm.sparc;

import sun.jvm.hotspot.asm.*;

class V9FMOVrDecoder extends V9CMoveDecoder {
    private final int opf;
    private final String name;
    private final int dataType;

    V9FMOVrDecoder(int opf, String name, int dataType) {
        this.opf = opf;
        this.name = name;
        this.dataType = dataType;
    }

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCV9InstructionFactory v9factory = (SPARCV9InstructionFactory) factory;
        int regConditionCode = getRegisterConditionCode(instruction);
        int rdNum = getDestinationRegister(instruction);
        int rs1Num = getSourceRegister1(instruction);
        int rs2Num = getSourceRegister2(instruction);
        SPARCRegister rd = RegisterDecoder.decode(dataType, rdNum);
        SPARCRegister rs2 = RegisterDecoder.decode(dataType, rs2Num);
        SPARCRegister rs1 = SPARCRegisters.getRegister(rs1Num);
        return v9factory.newV9FMOVrInstruction(name, opf, rs1, (SPARCFloatRegister)rs2,
                              (SPARCFloatRegister)rd, regConditionCode);
    }
}
