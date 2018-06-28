/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.isSimm13;
import static org.graalvm.compiler.lir.LIRValueUtil.asJavaConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.sparc.SPARCAddress;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler.ScratchRegister;
import org.graalvm.compiler.hotspot.HotSpotCounterOp;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

@Opcode("BenchMarkCounter")
public class SPARCHotSpotCounterOp extends HotSpotCounterOp {
    public static final LIRInstructionClass<SPARCHotSpotCounterOp> TYPE = LIRInstructionClass.create(SPARCHotSpotCounterOp.class);

    private int[] counterPatchOffsets;

    public SPARCHotSpotCounterOp(String name, String group, Value increment, HotSpotRegistersProvider registers, GraalHotSpotVMConfig config) {
        super(TYPE, name, group, increment, registers, config);
        this.counterPatchOffsets = new int[1];
    }

    public SPARCHotSpotCounterOp(String[] names, String[] groups, Value[] increments, HotSpotRegistersProvider registers, GraalHotSpotVMConfig config) {
        super(TYPE, names, groups, increments, registers, config);
        this.counterPatchOffsets = new int[names.length];
    }

    @Override
    public void emitCode(CompilationResultBuilder crb) {
        SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
        TargetDescription target = crb.target;

        // address for counters array
        SPARCAddress countersArrayAddr = new SPARCAddress(thread, config.jvmciCountersThreadOffset);
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register countersArrayReg = scratch.getRegister();

            // load counters array
            masm.ldx(countersArrayAddr, countersArrayReg);
            IncrementEmitter emitter = new IncrementEmitter(countersArrayReg, masm);
            forEachCounter(emitter, target);
        }
    }

    private void emitIncrement(int counterIndex, SPARCMacroAssembler masm, SPARCAddress counterAddr, Value increment) {
        try (ScratchRegister scratch = masm.getScratchRegister()) {
            Register counterReg = scratch.getRegister();
            // load counter value
            masm.ldx(counterAddr, counterReg);
            counterPatchOffsets[counterIndex] = masm.position();
            // increment counter
            if (isJavaConstant(increment)) {
                masm.add(counterReg, asInt(asJavaConstant(increment)), counterReg);
            } else {
                masm.add(counterReg, asRegister(increment), counterReg);
            }
            // store counter value
            masm.stx(counterReg, counterAddr);
        }
    }

    /**
     * Patches the increment value in the instruction emitted by the
     * {@link #emitIncrement(int, SPARCMacroAssembler, SPARCAddress, Value)} method. This method is
     * used if patching is needed after assembly.
     *
     * @param asm
     * @param increment
     */
    @Override
    public void patchCounterIncrement(Assembler asm, int[] increment) {
        for (int i = 0; i < increment.length; i++) {
            int inst = counterPatchOffsets[i];
            ((SPARCAssembler) asm).patchAddImmediate(inst, increment[i]);
        }
    }

    public int[] getCounterPatchOffsets() {
        return counterPatchOffsets;
    }

    private class IncrementEmitter implements CounterProcedure {
        private int lastDisplacement = 0;
        private final Register countersArrayReg;
        private final SPARCMacroAssembler masm;

        IncrementEmitter(Register countersArrayReg, SPARCMacroAssembler masm) {
            super();
            this.countersArrayReg = countersArrayReg;
            this.masm = masm;
        }

        @Override
        public void apply(int counterIndex, Value increment, int displacement) {
            SPARCAddress counterAddr;
            int relativeDisplacement = displacement - lastDisplacement;
            if (isSimm13(relativeDisplacement)) { // Displacement fits into ld instruction
                counterAddr = new SPARCAddress(countersArrayReg, relativeDisplacement);
            } else {
                try (ScratchRegister scratch = masm.getScratchRegister()) {
                    Register tempOffsetRegister = scratch.getRegister();
                    masm.setx(relativeDisplacement, tempOffsetRegister, false);
                    masm.add(countersArrayReg, tempOffsetRegister, countersArrayReg);
                }
                lastDisplacement = displacement;
                counterAddr = new SPARCAddress(countersArrayReg, 0);
            }
            emitIncrement(counterIndex, masm, counterAddr, increment);
        }
    }
}
