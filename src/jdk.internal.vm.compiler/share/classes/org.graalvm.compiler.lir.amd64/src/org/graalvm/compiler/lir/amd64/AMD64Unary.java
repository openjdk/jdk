/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MROp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.ImplicitNullCheck;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 LIR instructions that have one input and one output.
 */
public class AMD64Unary {

    /**
     * Instruction with a single operand that is both input and output.
     */
    public static class MOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<MOp> TYPE = LIRInstructionClass.create(MOp.class);

        @Opcode private final AMD64MOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue value;

        public MOp(AMD64MOp opcode, OperandSize size, AllocatableValue result, AllocatableValue value) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, value);
            opcode.emit(masm, size, asRegister(result));
        }
    }

    /**
     * Instruction with separate input and output operands, and an operand encoding of RM.
     */
    public static class RMOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<RMOp> TYPE = LIRInstructionClass.create(RMOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue value;

        public RMOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue value) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(value)) {
                opcode.emit(masm, size, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(value);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(value));
            }
        }
    }

    /**
     * Instruction with separate input and output operands, and an operand encoding of MR.
     */
    public static class MROp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<MROp> TYPE = LIRInstructionClass.create(MROp.class);

        @Opcode private final AMD64MROp opcode;
        private final OperandSize size;

        @Def({REG, STACK}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue value;

        public MROp(AMD64MROp opcode, OperandSize size, AllocatableValue result, AllocatableValue value) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.value = value;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(result)) {
                opcode.emit(masm, size, asRegister(result), asRegister(value));
            } else {
                assert isStackSlot(result);
                opcode.emit(masm, size, (AMD64Address) crb.asAddress(result), asRegister(value));
            }
        }
    }

    /**
     * Instruction with a {@link AMD64AddressValue memory} operand.
     */
    public static class MemoryOp extends AMD64LIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemoryOp> TYPE = LIRInstructionClass.create(MemoryOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({COMPOSITE}) protected AMD64AddressValue input;

        @State protected LIRFrameState state;

        public MemoryOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AMD64AddressValue input, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.input = input;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), input.toAddress());
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && input.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }
}
