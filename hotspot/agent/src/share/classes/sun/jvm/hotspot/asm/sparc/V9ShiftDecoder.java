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

class V9ShiftDecoder extends InstructionDecoder
                     implements V9InstructionDecoder {
    final int op3;
    final String name;
    final int rtlOperation;

    V9ShiftDecoder(int op3, String name, int rtlOperation) {
        this.op3 = op3;
        this.name = name;
        this.rtlOperation = rtlOperation;
    }

    static boolean isXBitSet(int instruction) {
        return (instruction & X_MASK) != 0;
    }

    Instruction decode(int instruction,
                       SPARCInstructionFactory factory) {
        SPARCRegister rs1 = SPARCRegisters.getRegister(getSourceRegister1(instruction));
        SPARCRegister rd = SPARCRegisters.getRegister(getDestinationRegister(instruction));
        boolean xBit = isXBitSet(instruction);
        ImmediateOrRegister operand2 = null;

        if (isIBitSet(instruction)) {
            // look for 64 bits shift operations.
            int value = instruction & ( xBit ? SHIFT_COUNT_6_MASK : SHIFT_COUNT_5_MASK);
            operand2 = new Immediate(new Short((short) value));
        } else {
            operand2 = SPARCRegisters.getRegister(getSourceRegister2(instruction));
        }

        return factory.newShiftInstruction(xBit? name + "x" : name, op3, rtlOperation, rs1, operand2, rd);
    }
}
