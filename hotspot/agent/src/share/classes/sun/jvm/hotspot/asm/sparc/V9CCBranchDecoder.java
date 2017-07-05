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

abstract class V9CCBranchDecoder extends V9BranchDecoder {
    abstract int getConditionFlag(int instruction);

    Instruction decode(int instruction, SPARCInstructionFactory factory) {
        SPARCV9InstructionFactory v9factory = (SPARCV9InstructionFactory) factory;
        int conditionFlag = getConditionFlag(instruction);
        boolean predictTaken = getPredictTaken(instruction);
        int conditionCode = getConditionCode(instruction);
        boolean annuled = getAnnuledBit(instruction);
        String name = getConditionName(conditionCode, annuled);
        // signed word aligned 19 bit
        PCRelativeAddress addr = new PCRelativeAddress(extractSignedIntFromNBits(instruction, 19) << 2);
        return v9factory.newV9BranchInstruction(name, addr, annuled, conditionCode,
                                              predictTaken, conditionFlag);
    }
}
