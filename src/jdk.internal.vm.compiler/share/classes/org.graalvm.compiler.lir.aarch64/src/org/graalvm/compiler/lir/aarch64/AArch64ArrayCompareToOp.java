/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ConditionFlag;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import sun.misc.Unsafe;

/**
 * Emits code which compares two arrays lexicographically. If the CPU supports any vector
 * instructions specialized code is emitted to leverage these instructions.
 */
@Opcode("ARRAY_COMPARE_TO")
public final class AArch64ArrayCompareToOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64ArrayCompareToOp> TYPE = LIRInstructionClass.create(AArch64ArrayCompareToOp.class);

    private final JavaKind kind1;
    private final JavaKind kind2;

    private final int array1BaseOffset;
    private final int array2BaseOffset;

    @Def({REG}) protected Value resultValue;

    @Alive({REG}) protected Value array1Value;
    @Alive({REG}) protected Value array2Value;
    @Use({REG}) protected Value length1Value;
    @Use({REG}) protected Value length2Value;
    @Temp({REG}) protected Value length1ValueTemp;
    @Temp({REG}) protected Value length2ValueTemp;

    @Temp({REG}) protected Value temp1;
    @Temp({REG}) protected Value temp2;
    @Temp({REG}) protected Value temp3;
    @Temp({REG}) protected Value temp4;
    @Temp({REG}) protected Value temp5;
    @Temp({REG}) protected Value temp6;

    public AArch64ArrayCompareToOp(LIRGeneratorTool tool, JavaKind kind1, JavaKind kind2, Value result, Value array1, Value array2, Value length1, Value length2) {
        super(TYPE);
        this.kind1 = kind1;
        this.kind2 = kind2;

        // Both offsets should be the same but better be safe than sorry.
        Class<?> array1Class = Array.newInstance(kind1.toJavaClass(), 0).getClass();
        Class<?> array2Class = Array.newInstance(kind2.toJavaClass(), 0).getClass();
        this.array1BaseOffset = UNSAFE.arrayBaseOffset(array1Class);
        this.array2BaseOffset = UNSAFE.arrayBaseOffset(array2Class);

        this.resultValue = result;

        this.array1Value = array1;
        this.array2Value = array2;

        /*
         * The length values are inputs but are also killed like temporaries so need both Use and
         * Temp annotations, which will only work with fixed registers.
         */

        this.length1Value = length1;
        this.length2Value = length2;
        this.length1ValueTemp = length1;
        this.length2ValueTemp = length2;

        // Allocate some temporaries.
        this.temp1 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp2 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp3 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp4 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp5 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
        this.temp6 = tool.newVariable(LIRKind.unknownReference(tool.target().arch.getWordKind()));
    }

    private static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    @Override
    protected void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {

        Register result = asRegister(resultValue);
        Register length1 = asRegister(length1Value);
        Register length2 = asRegister(length2Value);

        Register array1 = asRegister(temp1);
        Register array2 = asRegister(temp2);
        Register length = asRegister(temp3);
        Register temp = asRegister(temp4);
        Register tailCount = asRegister(temp5);
        Register vecCount = asRegister(temp6);

        // Checkstyle: stop
        final Label BREAK_LABEL = new Label();
        final Label STRING_DIFFER_LABEL = new Label();
        final Label LENGTH_DIFFER_LABEL = new Label();
        final Label MAIN_LOOP_LABEL = new Label();
        final Label COMPARE_SHORT_LABEL = new Label();
        // Checkstyle: resume

        // Checkstyle: stop
        int CHAR_SIZE_BYTES = 1;
        int VECTOR_SIZE_BYTES = 8;
        int VECTOR_COUNT_BYTES = 8;
        // Checkstyle: resume

        // Byte is expanded to short if we compare strings with different encoding
        if (kind1 != kind2 || kind1 == JavaKind.Char) {
            CHAR_SIZE_BYTES = 2;
        }

        if (kind1 != kind2) {
            VECTOR_COUNT_BYTES = 4;
        }

        // Load array base addresses.
        masm.lea(array1, AArch64Address.createUnscaledImmediateAddress(asRegister(array1Value), array1BaseOffset));
        masm.lea(array2, AArch64Address.createUnscaledImmediateAddress(asRegister(array2Value), array2BaseOffset));

        // Calculate minimal length in chars for different kind case
        // Conditions could be squashed but lets keep it readable
        if (kind1 != kind2) {
            masm.lshr(64, length2, length2, 1);
        }

        if (kind1 == kind2 && kind1 == JavaKind.Char) {
            masm.lshr(64, length1, length1, 1);
            masm.lshr(64, length2, length2, 1);
        }

        masm.cmp(64, length1, length2);
        masm.cmov(64, length, length1, length2, ConditionFlag.LT);

        // One of strings is empty
        masm.cbz(64, length, LENGTH_DIFFER_LABEL);

        // Go back to bytes if necessary
        if (kind1 != kind2 || kind1 == JavaKind.Char) {
            masm.shl(64, length, length, 1);
        }

        masm.mov(64, vecCount, zr);
        masm.and(64, tailCount, length, VECTOR_SIZE_BYTES - 1); // tail count (in bytes)
        masm.ands(64, length, length, ~(VECTOR_SIZE_BYTES - 1));  // vector count (in bytes)

        // Length of string is less than VECTOR_SIZE, go to simple compare
        masm.branchConditionally(ConditionFlag.EQ, COMPARE_SHORT_LABEL);

        // MAIN_LOOP - read strings by 8 byte.
        masm.bind(MAIN_LOOP_LABEL);
        if (kind1 != kind2) {
            // Load 32 bits ad unpack it to entire 64bit register
            masm.ldr(32, result, AArch64Address.createRegisterOffsetAddress(array1, vecCount, false));
            masm.ubfm(64, temp, result, 0, 7);
            masm.lshr(64, result, result, 8);
            masm.bfm(64, temp, result, 48, 7);
            masm.lshr(64, result, result, 8);
            masm.bfm(64, temp, result, 32, 7);
            masm.lshr(64, result, result, 8);
            masm.bfm(64, temp, result, 16, 7);
            // Unpacked value placed in temp now

            masm.shl(64, result, vecCount, 1);
            masm.ldr(64, result, AArch64Address.createRegisterOffsetAddress(array2, result, false));
        } else {
            masm.ldr(64, temp, AArch64Address.createRegisterOffsetAddress(array1, vecCount, false));
            masm.ldr(64, result, AArch64Address.createRegisterOffsetAddress(array2, vecCount, false));
        }
        masm.eor(64, result, temp, result);
        masm.cbnz(64, result, STRING_DIFFER_LABEL);
        masm.add(64, vecCount, vecCount, VECTOR_COUNT_BYTES);
        masm.cmp(64, vecCount, length);
        masm.branchConditionally(ConditionFlag.LT, MAIN_LOOP_LABEL);
        // End of MAIN_LOOP

        // Strings are equal and no TAIL go to END
        masm.cbz(64, tailCount, LENGTH_DIFFER_LABEL);

        // Compaire tail of long string ...
        masm.lea(array1, AArch64Address.createRegisterOffsetAddress(array1, length, false));
        masm.lea(array2, AArch64Address.createRegisterOffsetAddress(array2, length, false));

        // ... or string less than vector length
        masm.bind(COMPARE_SHORT_LABEL);
        for (int i = 0; i < VECTOR_COUNT_BYTES; i += CHAR_SIZE_BYTES) {
            if (kind1 != kind2) {
                masm.ldr(8, temp, AArch64Address.createUnscaledImmediateAddress(array1, i / 2));
            } else {
                masm.ldr(8 * CHAR_SIZE_BYTES, temp, AArch64Address.createUnscaledImmediateAddress(array1, i));
            }

            masm.ldr(8 * CHAR_SIZE_BYTES, result, AArch64Address.createUnscaledImmediateAddress(array2, i));

            if (kind1 != kind2 && kind1 == JavaKind.Char) {
                // Weird swap of substraction order
                masm.subs(64, result, result, temp);
            } else {
                masm.subs(64, result, temp, result);
            }

            masm.branchConditionally(ConditionFlag.NE, BREAK_LABEL);
            masm.subs(64, tailCount, tailCount, CHAR_SIZE_BYTES);
            masm.branchConditionally(ConditionFlag.EQ, LENGTH_DIFFER_LABEL);
        }

        // STRING_DIFFER extract exact value of a difference
        masm.bind(STRING_DIFFER_LABEL);
        masm.rbit(64, tailCount, result);
        masm.clz(64, vecCount, tailCount);
        masm.and(64, vecCount, vecCount, ~((8 * CHAR_SIZE_BYTES) - 1)); // Round to byte or short

        masm.eor(64, result, temp, result);
        masm.ashr(64, result, result, vecCount);
        masm.ashr(64, temp, temp, vecCount);

        masm.and(64, result, result, 0xFFFF >>> (16 - (8 * CHAR_SIZE_BYTES))); // 0xFF or 0xFFFF
        masm.and(64, temp, temp, 0xFFFF >>> (16 - (8 * CHAR_SIZE_BYTES)));

        masm.sub(64, result, temp, result);
        masm.branchConditionally(ConditionFlag.AL, BREAK_LABEL);
        // End of STRING_DIFFER

        // Strings are equials up to length,
        // return length difference in chars
        masm.bind(LENGTH_DIFFER_LABEL);
        if (kind1 != kind2 && kind1 == JavaKind.Char) {
            // Weird swap of substraction order
            masm.sub(64, result, length2, length1);
        } else {
            masm.sub(64, result, length1, length2);
        }

        // We are done
        masm.bind(BREAK_LABEL);
    }

} // class
