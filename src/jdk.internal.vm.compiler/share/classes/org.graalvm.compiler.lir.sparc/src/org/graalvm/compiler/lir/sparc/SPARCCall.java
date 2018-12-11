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


package org.graalvm.compiler.lir.sparc;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.ILLEGAL;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.sparc.SPARC.o7;

import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.InvokeTarget;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

public class SPARCCall {

    public abstract static class CallOp extends SPARCLIRInstruction {
        @Def({REG, ILLEGAL}) protected Value result;
        @Use({REG, STACK}) protected Value[] parameters;
        @Temp({REG, STACK}) protected Value[] temps;
        @State protected LIRFrameState state;

        protected CallOp(LIRInstructionClass<? extends CallOp> c, SizeEstimate size, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, size);
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

        protected final ResolvedJavaMethod callTarget;

        protected MethodCallOp(LIRInstructionClass<? extends MethodCallOp> c, SizeEstimate size, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, size, result, parameters, temps, state);
            this.callTarget = callTarget;
        }

    }

    @Opcode("CALL_DIRECT")
    public abstract static class DirectCallOp extends MethodCallOp {
        private boolean emitted = false;
        private int before = -1;

        public DirectCallOp(LIRInstructionClass<? extends DirectCallOp> c, SizeEstimate size, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, size, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            if (!emitted) {
                emitCallPrefixCode(crb, masm);
                directCall(crb, masm, callTarget, null, state);
            } else {
                int after = masm.position();
                if (after - before == 4) {
                    masm.nop();
                } else if (after - before == 8) {
                    // everything is fine;
                } else {
                    GraalError.shouldNotReachHere("" + (after - before));
                }
                after = masm.position();
                crb.recordDirectCall(before, after, callTarget, state);
                crb.recordExceptionHandlers(after, state);
                masm.ensureUniquePC();
            }
        }

        @SuppressWarnings("unused")
        public void emitCallPrefixCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            //
        }

        public void emitControlTransfer(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            assert !emitted;
            emitCallPrefixCode(crb, masm);
            before = masm.call(0);
            emitted = true;
        }

        public void resetState() {
            emitted = false;
            before = -1;
        }
    }

    @Opcode("CALL_INDIRECT")
    public abstract static class IndirectCallOp extends MethodCallOp {
        @Use({REG}) protected Value targetAddress;

        protected IndirectCallOp(LIRInstructionClass<? extends IndirectCallOp> c, SizeEstimate size, ResolvedJavaMethod callTarget, Value result, Value[] parameters, Value[] temps,
                        Value targetAddress, LIRFrameState state) {
            super(c, size, callTarget, result, parameters, temps, state);
            this.targetAddress = targetAddress;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            indirectCall(crb, masm, asRegister(targetAddress), callTarget, state);
        }

        @Override
        public void verify() {
            super.verify();
            assert isRegister(targetAddress) : "The current register allocator cannot handle variables to be used at call sites, it must be in a fixed register for now";
        }
    }

    public abstract static class ForeignCallOp extends CallOp {
        public static final LIRInstructionClass<ForeignCallOp> TYPE = LIRInstructionClass.create(ForeignCallOp.class);

        protected final ForeignCallLinkage callTarget;

        public ForeignCallOp(LIRInstructionClass<? extends ForeignCallOp> c, SizeEstimate size, ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(c, size, result, parameters, temps, state);
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
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        public DirectNearForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, SIZE, linkage, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            directCall(crb, masm, callTarget, null, state);
        }
    }

    @Opcode("FAR_FOREIGN_CALL")
    public static final class DirectFarForeignCallOp extends ForeignCallOp {
        public static final LIRInstructionClass<DirectFarForeignCallOp> TYPE = LIRInstructionClass.create(DirectFarForeignCallOp.class);
        public static final SizeEstimate SIZE = SizeEstimate.create(1);

        public DirectFarForeignCallOp(ForeignCallLinkage callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState state) {
            super(TYPE, SIZE, callTarget, result, parameters, temps, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
            try (ScratchRegister scratch = masm.getScratchRegister()) {
                directCall(crb, masm, callTarget, scratch.getRegister(), state);
            }
        }
    }

    public static void directCall(CompilationResultBuilder crb, SPARCMacroAssembler masm, InvokeTarget callTarget, Register scratch, LIRFrameState info) {
        int before;
        if (scratch != null) {
            // offset might not fit a 30-bit displacement, generate an
            // indirect call with a 64-bit immediate
            before = masm.position();
            masm.sethix(0L, scratch, true);
            masm.jmpl(scratch, 0, o7);
        } else {
            before = masm.call(0);
        }
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordDirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }

    public static void indirectJmp(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget target) {
        int before = masm.position();
        masm.sethix(0L, dst, true);
        masm.jmp(new SPARCAddress(dst, 0));
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, target, null);
        masm.ensureUniquePC();
    }

    public static void indirectCall(CompilationResultBuilder crb, SPARCMacroAssembler masm, Register dst, InvokeTarget callTarget, LIRFrameState info) {
        int before = masm.jmpl(dst, 0, o7);
        masm.nop();  // delay slot
        int after = masm.position();
        crb.recordIndirectCall(before, after, callTarget, info);
        crb.recordExceptionHandlers(after, info);
        masm.ensureUniquePC();
    }
}
