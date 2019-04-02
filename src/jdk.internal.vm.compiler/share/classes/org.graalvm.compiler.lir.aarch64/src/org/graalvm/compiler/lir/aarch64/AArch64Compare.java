/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;

import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AArch64Compare {

    public static class CompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompareOp> TYPE = LIRInstructionClass.create(CompareOp.class);

        @Use protected Value x;
        @Use({REG, CONST}) protected Value y;

        public CompareOp(Value x, Value y) {
            super(TYPE);
            assert x.getPlatformKind() == y.getPlatformKind() : x.getPlatformKind() + " " + y.getPlatformKind();
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            gpCompare(masm, x, y);
        }
    }

    /**
     * Compares integer values x and y.
     *
     * @param x integer value to compare. May not be null.
     * @param y integer value to compare. May not be null.
     */
    public static void gpCompare(AArch64MacroAssembler masm, Value x, Value y) {
        final int size = x.getPlatformKind().getSizeInBytes() * Byte.SIZE;
        if (isRegister(y)) {
            masm.cmp(size, asRegister(x), asRegister(y));
        } else {
            JavaConstant constant = asJavaConstant(y);
            if (constant.isDefaultForKind()) {
                masm.cmp(size, asRegister(x), 0);
            } else {
                final long longValue = constant.asLong();
                assert NumUtil.isInt(longValue);
                int maskedValue;
                switch (constant.getJavaKind()) {
                    case Boolean:
                    case Byte:
                        maskedValue = (int) (longValue & 0xFF);
                        break;
                    case Char:
                    case Short:
                        maskedValue = (int) (longValue & 0xFFFF);
                        break;
                    case Int:
                    case Long:
                        maskedValue = (int) longValue;
                        break;
                    default:
                        throw GraalError.shouldNotReachHere();
                }
                masm.cmp(size, asRegister(x), maskedValue);
            }
        }
    }

    public static class FloatCompareOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<FloatCompareOp> TYPE = LIRInstructionClass.create(FloatCompareOp.class);

        @Use protected Value x;
        @Use({REG, CONST}) protected Value y;
        private final Condition condition;
        private final boolean unorderedIsTrue;

        public FloatCompareOp(Value x, Value y, Condition condition, boolean unorderedIsTrue) {
            super(TYPE);
            assert !isJavaConstant(y) || isFloatCmpConstant(y, condition, unorderedIsTrue);
            this.x = x;
            this.y = y;
            this.condition = condition;
            this.unorderedIsTrue = unorderedIsTrue;
        }

        /**
         * Checks if val can be used as a constant for the gpCompare operation or not.
         */
        public static boolean isFloatCmpConstant(Value val, Condition condition, boolean unorderedIsTrue) {
            // If the condition is "EQ || unordered" or "NE && unordered" we have to use 2 registers
            // in any case.
            if (!(condition == Condition.EQ && unorderedIsTrue || condition == Condition.NE && !unorderedIsTrue)) {
                return false;
            }
            return isJavaConstant(val) && asJavaConstant(val).isDefaultForKind();
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert isRegister(x);
            int size = x.getPlatformKind().getSizeInBytes() * Byte.SIZE;
            if (isRegister(y)) {
                masm.fcmp(size, asRegister(x), asRegister(y));
                // There is no condition code for "EQ || unordered" nor one for "NE && unordered",
                // so we have to fix them up ourselves.
                // In both cases we combine the asked for condition into the EQ, respectively NE
                // condition, i.e.
                // if EQ && unoreredIsTrue, then the EQ flag will be set if the two values gpCompare
                // unequal but are
                // unordered.
                if (condition == Condition.EQ && unorderedIsTrue) {
                    // if f1 ordered f2:
                    // result = f1 == f2
                    // else:
                    // result = EQUAL
                    int nzcv = 0b0100;   // EQUAL -> Z = 1
                    masm.fccmp(size, asRegister(x), asRegister(y), nzcv, AArch64Assembler.ConditionFlag.VC);
                } else if (condition == Condition.NE && !unorderedIsTrue) {
                    // if f1 ordered f2:
                    // result = f1 != f2
                    // else:
                    // result = !NE == EQUAL
                    int nzcv = 0b0100;   // EQUAL -> Z = 1
                    masm.fccmp(size, asRegister(x), asRegister(y), nzcv, AArch64Assembler.ConditionFlag.VC);
                }
            } else {
                // cmp against +0.0
                masm.fcmpZero(size, asRegister(x));
            }
        }

        @Override
        public void verify() {
            assert x.getPlatformKind().equals(y.getPlatformKind()) : "a: " + x + " b: " + y;
        }
    }

}
