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

class FPArithmeticDecoder extends FloatDecoder {
    private final int rtlOperation;

    FPArithmeticDecoder(int opf, String name, int rtlOperation,
                        int src1Type, int src2Type, int resultType) {
       super(opf, name, src1Type, src2Type, resultType);
       this.rtlOperation = rtlOperation;
    }

    Instruction decodeFloatInstruction(int instruction,
                       SPARCRegister rs1, SPARCRegister rs2,
                       SPARCRegister rd,
                       SPARCInstructionFactory factory) {
        if (Assert.ASSERTS_ENABLED)
            Assert.that(rs1.isFloat() && rs2.isFloat() && rd.isFloat(), "rs1, rs2 and rd must be floats");
        return factory.newFPArithmeticInstruction(name, opf, rtlOperation,
                                                 (SPARCFloatRegister)rs1,
                                                 (SPARCFloatRegister)rs2,
                                                 (SPARCFloatRegister)rd);
    }
}
