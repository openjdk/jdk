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

// basic instruction decoder class
abstract class InstructionDecoder implements /* imports */ SPARCOpcodes , RTLDataTypes, RTLOperations {
    // some general utility functions - for format 2, 3 & 3A instructions

    static int extractSignedIntFromNBits(int value, int num_bits) {
        return (value << (32 - num_bits)) >> (32 - num_bits);
    }

    // "rs1"
    static int getSourceRegister1(int instruction) {
        return (instruction & RS1_MASK) >>> RS1_START_BIT;
    }

    // "rs2"
    static int getSourceRegister2(int instruction) {
        return (instruction & RS2_MASK);
    }

    // "rd"
    static int getDestinationRegister(int instruction) {
        return (instruction & RD_MASK) >>> RD_START_BIT;
    }

    static int getConditionCode(int instruction) {
        return (instruction & CONDITION_CODE_MASK) >>> CONDITION_CODE_START_BIT;
    }

    // "i" bit - used to indicate whether second component in an indirect
    // address is immediate value or a register. (format 3 & 3A).

    static boolean isIBitSet(int instruction) {
        return (instruction & I_MASK) != 0;
    }

    static ImmediateOrRegister getOperand2(int instruction) {
        boolean iBit = isIBitSet(instruction);
        ImmediateOrRegister operand2 = null;
        if (iBit) {
           operand2 = new Immediate(new Short((short)extractSignedIntFromNBits(instruction, 13)));
        } else {
           operand2 = SPARCRegisters.getRegister(getSourceRegister2(instruction));
        }
        return operand2;
    }

    // "opf" - floating point operation code.
    static int getOpf(int instruction) {
        return (instruction & OPF_MASK) >>> OPF_START_BIT;
    }

    abstract Instruction decode(int instruction, SPARCInstructionFactory factory);
}
