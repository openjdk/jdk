/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRValueUtil.differentRegisters;
import static org.graalvm.compiler.lir.LIRValueUtil.sameRegister;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64MIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMIOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.AMD64RRMOp;
import org.graalvm.compiler.asm.amd64.AMD64Assembler.OperandSize;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.StandardOp.ImplicitNullCheck;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * AMD64 LIR instructions that have two inputs and one output.
 */
public class AMD64Binary {

    /**
     * Instruction that has two {@link AllocatableValue} operands.
     */
    public static class TwoOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<TwoOp> TYPE = LIRInstructionClass.create(TwoOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        /**
         * This argument must be Alive to ensure that result and y are not assigned to the same
         * register, which would break the code generation by destroying y too early.
         */
        @Alive({REG, STACK}) protected AllocatableValue y;

        public TwoOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(y));
            } else {
                assert isStackSlot(y);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    /**
     * Instruction that has three {@link AllocatableValue} operands.
     */
    public static class ThreeOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ThreeOp> TYPE = LIRInstructionClass.create(ThreeOp.class);

        @Opcode private final AMD64RRMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public ThreeOp(AMD64RRMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), asRegister(y));
            } else {
                assert isStackSlot(y);
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    /**
     * Commutative instruction that has two {@link AllocatableValue} operands.
     */
    public static class CommutativeTwoOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CommutativeTwoOp> TYPE = LIRInstructionClass.create(CommutativeTwoOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public CommutativeTwoOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AllocatableValue input;
            if (sameRegister(result, y)) {
                input = x;
            } else {
                AMD64Move.move(crb, masm, result, x);
                input = y;
            }

            if (isRegister(input)) {
                opcode.emit(masm, size, asRegister(result), asRegister(input));
            } else {
                assert isStackSlot(input);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(input));
            }
        }
    }

    /**
     * Commutative instruction that has three {@link AllocatableValue} operands.
     */
    public static class CommutativeThreeOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CommutativeThreeOp> TYPE = LIRInstructionClass.create(CommutativeThreeOp.class);

        @Opcode private final AMD64RRMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public CommutativeThreeOp(AMD64RRMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AllocatableValue y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), asRegister(y));
            } else {
                assert isStackSlot(y);
                opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one 32-bit immediate operand.
     */
    public static class ConstOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ConstOp> TYPE = LIRInstructionClass.create(ConstOp.class);

        @Opcode private final AMD64MIOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        private final int y;

        public ConstOp(AMD64BinaryArithmetic opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            this(opcode.getMIOpcode(size, NumUtil.isByte(y)), size, result, x, y);
        }

        public ConstOp(AMD64MIOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            opcode.emit(masm, size, asRegister(result), y);
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one
     * {@link DataSectionReference} operand.
     */
    public static class DataTwoOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<DataTwoOp> TYPE = LIRInstructionClass.create(DataTwoOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        private final JavaConstant y;

        private final int alignment;

        public DataTwoOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y) {
            this(opcode, size, result, x, y, y.getJavaKind().getByteCount());
        }

        public DataTwoOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y, int alignment) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.alignment = alignment;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.recordDataReferenceInCode(y, alignment));
        }
    }

    /**
     * Instruction that has two {@link AllocatableValue} operands and one
     * {@link DataSectionReference} operand.
     */
    public static class DataThreeOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<DataThreeOp> TYPE = LIRInstructionClass.create(DataThreeOp.class);

        @Opcode private final AMD64RRMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        private final JavaConstant y;

        private final int alignment;

        public DataThreeOp(AMD64RRMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y) {
            this(opcode, size, result, x, y, y.getJavaKind().getByteCount());
        }

        public DataThreeOp(AMD64RRMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, JavaConstant y, int alignment) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.alignment = alignment;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            opcode.emit(masm, size, asRegister(result), asRegister(x), (AMD64Address) crb.recordDataReferenceInCode(y, alignment));
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one {@link AMD64AddressValue
     * memory} operand.
     */
    public static class MemoryTwoOp extends AMD64LIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemoryTwoOp> TYPE = LIRInstructionClass.create(MemoryTwoOp.class);

        @Opcode private final AMD64RMOp opcode;
        private final OperandSize size;

        @Def({REG, HINT}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Alive({COMPOSITE}) protected AMD64AddressValue y;

        @State protected LIRFrameState state;

        public MemoryTwoOp(AMD64RMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AMD64AddressValue y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            AMD64Move.move(crb, masm, result, x);
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), y.toAddress());
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && y.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    /**
     * Instruction that has one {@link AllocatableValue} operand and one {@link AMD64AddressValue
     * memory} operand.
     */
    public static class MemoryThreeOp extends AMD64LIRInstruction implements ImplicitNullCheck {
        public static final LIRInstructionClass<MemoryThreeOp> TYPE = LIRInstructionClass.create(MemoryThreeOp.class);

        @Opcode private final AMD64RRMOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG}) protected AllocatableValue x;
        @Use({COMPOSITE}) protected AMD64AddressValue y;

        @State protected LIRFrameState state;

        public MemoryThreeOp(AMD64RRMOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, AMD64AddressValue y, LIRFrameState state) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;

            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (state != null) {
                crb.recordImplicitException(masm.position(), state);
            }
            opcode.emit(masm, size, asRegister(result), asRegister(x), y.toAddress());
        }

        @Override
        public void verify() {
            super.verify();
            assert differentRegisters(result, y) || sameRegister(x, y);
        }

        @Override
        public boolean makeNullCheckFor(Value value, LIRFrameState nullCheckState, int implicitNullCheckLimit) {
            if (state == null && y.isValidImplicitNullCheckFor(value, implicitNullCheckLimit)) {
                state = nullCheckState;
                return true;
            }
            return false;
        }
    }

    /**
     * Instruction with a separate result operand, one {@link AllocatableValue} input and one 32-bit
     * immediate input.
     */
    public static class RMIOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<RMIOp> TYPE = LIRInstructionClass.create(RMIOp.class);

        @Opcode private final AMD64RMIOp opcode;
        private final OperandSize size;

        @Def({REG}) protected AllocatableValue result;
        @Use({REG, STACK}) protected AllocatableValue x;
        private final int y;

        public RMIOp(AMD64RMIOp opcode, OperandSize size, AllocatableValue result, AllocatableValue x, int y) {
            super(TYPE);
            this.opcode = opcode;
            this.size = size;

            this.result = result;
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(x)) {
                opcode.emit(masm, size, asRegister(result), asRegister(x), y);
            } else {
                assert isStackSlot(x);
                opcode.emit(masm, size, asRegister(result), (AMD64Address) crb.asAddress(x), y);
            }
        }
    }
}
