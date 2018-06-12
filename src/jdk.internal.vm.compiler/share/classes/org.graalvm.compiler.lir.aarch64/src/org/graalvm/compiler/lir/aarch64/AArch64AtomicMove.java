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

import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ShiftType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class AArch64AtomicMove {
    /**
     * Compare and swap instruction. Does the following atomically: <code>
     *  CAS(newVal, expected, address):
     *    oldVal = *address
     *    if oldVal == expected:
     *        *address = newVal
     *    return oldVal
     * </code>
     */
    @Opcode("CAS")
    public static class CompareAndSwapOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);

        @Def protected AllocatableValue resultValue;
        @Alive protected Value expectedValue;
        @Alive protected AllocatableValue newValue;
        @Alive protected AllocatableValue addressValue;
        @Temp protected AllocatableValue scratchValue;

        public CompareAndSwapOp(AllocatableValue result, Value expectedValue, AllocatableValue newValue, AllocatableValue addressValue, AllocatableValue scratch) {
            super(TYPE);
            this.resultValue = result;
            this.expectedValue = expectedValue;
            this.newValue = newValue;
            this.addressValue = addressValue;
            this.scratchValue = scratch;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            AArch64Kind kind = (AArch64Kind) expectedValue.getPlatformKind();
            assert kind.isInteger();
            final int size = kind.getSizeInBytes() * Byte.SIZE;

            Register address = asRegister(addressValue);
            Register result = asRegister(resultValue);
            Register newVal = asRegister(newValue);
            if (AArch64LIRFlagsVersioned.useLSE(masm)) {
                Register expected = asRegister(expectedValue);
                masm.mov(size, result, expected);
                masm.cas(size, expected, newVal, address, true /* acquire */, true /* release */);
                AArch64Compare.gpCompare(masm, resultValue, expectedValue);
            } else {
                // We could avoid using a scratch register here, by reusing resultValue for the
                // stlxr success flag and issue a mov resultValue, expectedValue in case of success
                // before returning.
                Register scratch = asRegister(scratchValue);
                Label retry = new Label();
                Label fail = new Label();
                masm.bind(retry);
                masm.ldaxr(size, result, address);
                AArch64Compare.gpCompare(masm, resultValue, expectedValue);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, fail);
                masm.stlxr(size, scratch, newVal, address);
                // if scratch == 0 then write successful, else retry.
                masm.cbnz(32, scratch, retry);
                masm.bind(fail);
            }
        }
    }

    /**
     * Load (Read) and Add instruction. Does the following atomically: <code>
     *  ATOMIC_READ_AND_ADD(addend, result, address):
     *    result = *address
     *    *address = result + addend
     *    return result
     * </code>
     */
    @Opcode("ATOMIC_READ_AND_ADD")
    public static final class AtomicReadAndAddOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndAddOp> TYPE = LIRInstructionClass.create(AtomicReadAndAddOp.class);

        private final AArch64Kind accessKind;

        @Def protected AllocatableValue resultValue;
        @Alive protected AllocatableValue addressValue;
        @Alive protected Value deltaValue;
        @Temp protected AllocatableValue scratchValue1;
        @Temp protected AllocatableValue scratchValue2;

        public AtomicReadAndAddOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, Value delta, AllocatableValue scratch1, AllocatableValue scratch2) {
            super(TYPE);
            this.accessKind = kind;
            this.resultValue = result;
            this.addressValue = address;
            this.deltaValue = delta;
            this.scratchValue1 = scratch1;
            this.scratchValue2 = scratch2;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert accessKind.isInteger();
            final int size = accessKind.getSizeInBytes() * Byte.SIZE;

            Register address = asRegister(addressValue);
            Register delta = asRegister(deltaValue);
            Register result = asRegister(resultValue);

            if (AArch64LIRFlagsVersioned.useLSE(masm)) {
                masm.ldadd(size, delta, result, address, true, true);
            } else {
                Register scratch1 = asRegister(scratchValue1);
                Register scratch2 = asRegister(scratchValue2);
                Label retry = new Label();
                masm.bind(retry);
                masm.ldaxr(size, result, address);
                masm.add(size, scratch1, result, delta, ShiftType.LSL, 0);
                masm.stlxr(size, scratch2, scratch1, address);
                // if scratch2 == 0 then write successful, else retry
                masm.cbnz(32, scratch2, retry);
            }
        }
    }
}
