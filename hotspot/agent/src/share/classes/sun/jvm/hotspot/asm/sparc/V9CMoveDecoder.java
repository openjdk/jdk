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

abstract class V9CMoveDecoder extends InstructionDecoder
                   implements V9InstructionDecoder {
    static private final String iccConditionNames[] = {
        "n", "e", "le", "l", "leu", "cs", "neg", "vs",
        "a", "ne", "g", "ge", "gu", "cc", "pos", "vc"
    };

    static private final String fccConditionNames[] = {
        "fn", "fne", "flg", "ful", "fl", "fug", "fg", "fu",
        "fa", "fe",  "fue", "fge", "fuge", "fle", "fule", "fo"
    };

    static String getConditionName(int conditionCode, int conditionFlag) {
        return (conditionFlag == icc || conditionFlag == xcc) ?
                       iccConditionNames[conditionCode]
                     : fccConditionNames[conditionCode];
    }

    static int getMoveConditionCode(int instruction) {
        return (instruction & CMOVE_COND_MASK) >>> CMOVE_COND_START_BIT;
    }

    static int getRegisterConditionCode(int instruction) {
        return (instruction & CMOVE_RCOND_MASK) >>> CMOVE_RCOND_START_BIT;
    }

    static ImmediateOrRegister getCMoveSource(int instruction, int numBits) {
        ImmediateOrRegister source = null;
        if (isIBitSet(instruction)) {
            source = new Immediate(new Short((short) extractSignedIntFromNBits(instruction, numBits)));
        } else {
            source = SPARCRegisters.getRegister(getSourceRegister2(instruction));
        }
        return source;
    }

    static String getFloatTypeCode(int dataType) {
        String result = null;
        switch(dataType) {
            case RTLDT_FL_SINGLE:
                result = "s";
                break;

            case RTLDT_FL_DOUBLE:
                result = "d";
                break;

            case RTLDT_FL_QUAD:
                result = "q";
                break;

            default:
                if (Assert.ASSERTS_ENABLED)
                    Assert.that(false, "should not reach here");
        }
        return result;
    }
}
