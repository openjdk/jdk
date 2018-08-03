/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static org.graalvm.compiler.lir.LIRValueUtil.differentRegisters;

import org.graalvm.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapRegistersAfterInstruction;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class AMD64Call {

    public abstract static class CallOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<CallOp> TYPE = LIRInstructionClass.create(CallOp.class);

        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp({REG, STACK}) protected Value[] temps;
        @State protected LIRFrameState state;

        protected CallOp(LIRInstructionClass<? extends CallOp> c, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c);
            this.result = result;
            this.parameters = parameters;
            this.state = state;
            this.temps = addStackSlotsToTemporaries(parameters, temps);
            assert temps != null;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return true;
        }
    }

    public abstract static class MethodCallOp extends CallOp {
        public static final LIRInstructionClass<MethodCallOp> TYPE = LIRInstructionClass.create(MethodCallOp.class);

        protected final ResolvedJavaMethod callTarget;

        protected MethodCallOp(LIRInstructionClass<? extends MethodCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

    }

    @Opcode("CALL_DIRECT")
    public static class DirectCallOp extends MethodCallOp {
        public static final LIRInstructionClass<DirectCallOp> TYPE = LIRInstructionClass.create(DirectCallOp.class);

        public DirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            this(TYPE, callTarget, result, parameters, temps, state);
        }

        protected DirectCallOp(LIRInstructionClass<? extends DirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            directCall(crb, masm, callTarget, null, true, state);
        }

        public int emitCall(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            return directCall(crb, masm, callTarget, null, true, state);
        }
    }

    @Opcode("CALL_INDIRECT")
    public static class IndirectCallOp extends MethodCallOp {
        public static final LIRInstructionClass<IndirectCallOp> TYPE = LIRInstructionClass.create(IndirectCallOp.class);

        @Use({REG}) protected Value targetAddress;

        public IndirectCallOp(ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress, LIRFrameState state) {
            this(TYPE, callTarget, result, parameters, temps, targetAddress, state);
        }

        protected IndirectCallOp(LIRInstructionClass<? extends IndirectCallOp> c, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, Value targetAddress,
                        LIRFrameState state) {
            super(c, callTarget, result, parameters, temps, state);
            this.targetAddress = targetAddress;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
        }

        @Override
        public void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, it must be in a fixed register for now";
        }
    }

    public abstract static class ForeignCallOp extends CallOp implements ZapRegistersAfterInstruction {
        public static final LIRInstructionClass<ForeignCallOp> TYPE = LIRInstructionClass.create(ForeignCallOp.class);

        protected final ForeignCallLinkage callTarget;

        public ForeignCallOp(LIRInstructionClass<? extends ForeignCallOp> c, ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

        @Override
        public boolean destroysCallerSavedRegisters() {
            return callTarget.destroysRegisters();
        }
    }

    @Opcode("NEAR_FOREIGN_CALL")
    public static final class DirectNearForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectNearForeignCallOp> TYPE = LIRInstructionClass.create(DirectNearForeignCallOp.class);

        public DirectNearForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, linkage, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            directCall(crb, masm, callTarget, null, false, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static final class DirectFarForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectFarForeignCallOp> TYPE = LIRInstructionClass.create(DirectFarForeignCallOp.class);

        @Temp({REG}) protected AllocatableValue callTemp;

        public DirectFarForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, callTarget, result, parameters, temps, state);
            /*
             * The register allocator does not support virtual registers that are used at the call
             * site, so use a fixed register.
             */
            callTemp = AMD64.rax.asValue(LIRKind.value(AMD64Kind.QWORD));
            assert differentRegisters(parameters, callTemp);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            directCall(crb, masm, callTarget, ((RegisterValue) callTemp).getRegister(), false, state);
        }
    }

    public static int directCall(CompilationResultBuilder crb, AMD64MacroAssembler masm, InvokeTarget callTarget, Register scratch, boolean align, LIRFrameState info) {
        if (align) {
            emitAlignmentForDirectCall(crb, masm);
        }
        int before = masm.position();
        int callPCOffset;
        if (scratch != null && !GeneratePIC.getValue(crb.getOptions())) {
            // offset might not fit a 32-bit immediate, generate an
            // indirect call with a 64-bit immediate
            masm.movq(scratch, 0L);
            callPCOffset = masm.position();
            masm.call(scratch);
        } else {
            callPCOffset = masm.position();
            masm.call();
        }
        int after = masm.position();
        crb.recordDirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
        return callPCOffset;
    }

    protected static void emitAlignmentForDirectCall(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        // make sure that the displacement word of the call ends up word aligned
        int offset = masm.position();
        offset += crb.target.arch.getMachineCodeCallDisplacementOffset();
        int modulus = crb.target.wordSize;
        if (offset % modulus != 0) {
            masm.nop(modulus - offset % modulus);
        }
    }

    public static int directJmp(CompilationResultBuilder crb, AMD64MacroAssembler masm, InvokeTarget target) {
        return directJmp(crb, masm, target, null);
    }

    public static int directJmp(CompilationResultBuilder crb, AMD64MacroAssembler masm, InvokeTarget target, Register scratch) {
        int before = masm.position();
        int callPCOffset;
        if (scratch != null && !GeneratePIC.getValue(crb.getOptions())) {
            // offset might not fit a 32-bit immediate, generate an
            // indirect call with a 64-bit immediate
            masm.movq(scratch, 0L);
            callPCOffset = masm.position();
            masm.jmp(scratch);
        } else {
            callPCOffset = masm.position();
            masm.jmp(0, true);
        }
        int after = masm.position();
        crb.recordDirectCall(before, after, target, null);
        masm.ensureUniquePC();
        return callPCOffset;
    }

    public static void directConditionalJmp(CompilationResultBuilder crb, AMD64MacroAssembler masm, InvokeTarget target, ConditionFlag cond) {
        int before = masm.position();
        masm.jcc(cond, 0, true);
        int after = masm.position();
        crb.recordDirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static int indirectCall(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.position();
        masm.call(dst);
        int after = masm.position();
        crb.recordIndirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
        return before;
    }
}
