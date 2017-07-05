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

abstract class BranchDecoder extends InstructionDecoder {

    // format 2 - condition code names.
    // Appendix F - Opcodes and Condition Codes - Page 231 - Table F-7.
    static final String integerConditionNames[] = {
        "bn", "be", "ble", "bl", "bleu", "bcs", "bneg", "bvs",
        "ba", "bne", "bg", "bge", "bgu", "bcc", "bpos", "bvc"
    };

    static final String integerAnnuledConditionNames[] = {
        "bn,a", "be,a", "ble,a", "bl,a", "bleu,a", "bcs,a", "bneg,a", "bvs,a",
        "ba,a", "bne,a", "bg,a", "bge,a", "bgu,a", "bcc,a", "bpos,a", "bvc,a"
    };

    // format 2 - condition code names.
    // Appendix F - Opcodes and Condition Codes - Page 231 - Table F-7.
    static final String floatConditionNames[] = {
        "fbn", "fbne", "fblg", "fbul", "fbl", "fbug", "fbg", "fbu",
        "fba", "fbe",  "fbue", "fbge", "fbuge", "fble", "fbule", "fbo"
    };

    static final String floatAnnuledConditionNames[] = {
        "fbn,a", "fbne,a", "fblg,a", "fbul,a", "fbl,a", "fbug,a", "fbg,a", "fbu,a",
        "fba,a", "fbe,a",  "fbue,a", "fbge,a", "fbuge,a", "fble,a", "fbule,a", "fbo,a"
    };

    static boolean getAnnuledBit(int instruction) {
        return (instruction & ANNUL_MASK) != 0;
    }

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        boolean isAnnuled = getAnnuledBit(instruction);
        int conditionCode = getConditionCode(instruction);
        String conditionName = getConditionName(conditionCode, isAnnuled);
        int offset = extractSignedIntFromNBits(instruction, 22);
        // word align the offset by right shifting 2 bits
        offset <<= 2;
        PCRelativeAddress addr = new PCRelativeAddress(offset);
        return factory.newBranchInstruction(conditionName, addr, isAnnuled, conditionCode);
    }

    abstract String getConditionName(int conditionCode, boolean isAnnuled);
}
