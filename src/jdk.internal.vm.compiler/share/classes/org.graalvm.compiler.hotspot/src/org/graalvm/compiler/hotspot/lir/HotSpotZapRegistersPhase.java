/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.lir;

import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;

import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.ZapRegistersOp;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapRegistersAfterInstruction;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapStackArgumentSpaceBeforeInstruction;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * Inserts a {@link DiagnosticLIRGeneratorTool#createZapRegisters ZapRegistersOp} after
 * {@link ZapRegistersAfterInstruction} for stubs and
 * {@link DiagnosticLIRGeneratorTool#zapArgumentSpace ZapArgumentSpaceOp} after
 * {@link ZapStackArgumentSpaceBeforeInstruction} for all compiles.
 */
public final class HotSpotZapRegistersPhase extends PostAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        Stub stub = ((HotSpotLIRGenerationResult) lirGenRes).getStub();
        boolean zapRegisters = stub == null;
        boolean zapStack = false;
        CallingConvention callingConvention = lirGenRes.getCallingConvention();
        for (AllocatableValue arg : callingConvention.getArguments()) {
            if (isStackSlot(arg)) {
                zapStack = true;
                break;
            }
        }
        if (zapRegisters || zapStack) {
            LIR lir = lirGenRes.getLIR();
            EconomicSet<Register> allocatableRegisters = EconomicSet.create(Equivalence.IDENTITY);
            for (Register r : lirGenRes.getFrameMap().getRegisterConfig().getAllocatableRegisters()) {
                allocatableRegisters.add(r);
            }
            processLIR(context.diagnosticLirGenTool, lir, allocatableRegisters, zapRegisters, zapStack);
        }
    }

    private static void processLIR(DiagnosticLIRGeneratorTool diagnosticLirGenTool, LIR lir, EconomicSet<Register> allocatableRegisters, boolean zapRegisters, boolean zapStack) {
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block != null) {
                processBlock(diagnosticLirGenTool, lir, allocatableRegisters, buffer, block, zapRegisters, zapStack);
            }
        }
    }

    @SuppressWarnings("try")
    private static void processBlock(DiagnosticLIRGeneratorTool diagnosticLirGenTool, LIR lir, EconomicSet<Register> allocatableRegisters, LIRInsertionBuffer buffer, AbstractBlockBase<?> block,
                    boolean zapRegisters, boolean zapStack) {
        DebugContext debug = lir.getDebug();
        try (Indent indent = debug.logAndIndent("Process block %s", block)) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            buffer.init(instructions);
            for (int index = 0; index < instructions.size(); index++) {
                LIRInstruction inst = instructions.get(index);
                if (zapStack && inst instanceof ZapStackArgumentSpaceBeforeInstruction) {
                    LIRInstruction zap = diagnosticLirGenTool.zapArgumentSpace();
                    if (zap != null) {
                        buffer.append(index, zap);
                    }
                }
                if (zapRegisters && inst instanceof ZapRegistersAfterInstruction) {
                    final EconomicSet<Register> destroyedRegisters = EconomicSet.create(Equivalence.IDENTITY);
                    ValueConsumer tempConsumer = (value, mode, flags) -> {
                        if (ValueUtil.isRegister(value)) {
                            final Register reg = ValueUtil.asRegister(value);
                            if (allocatableRegisters.contains(reg)) {
                                destroyedRegisters.add(reg);
                            }
                        }
                    };
                    ValueConsumer defConsumer = (value, mode, flags) -> {
                        if (ValueUtil.isRegister(value)) {
                            final Register reg = ValueUtil.asRegister(value);
                            destroyedRegisters.remove(reg);
                        }
                    };
                    inst.visitEachTemp(tempConsumer);
                    inst.visitEachOutput(defConsumer);

                    ZapRegistersOp zap = diagnosticLirGenTool.createZapRegisters(destroyedRegisters.toArray(new Register[destroyedRegisters.size()]));
                    buffer.append(index + 1, (LIRInstruction) zap);
                    debug.log("Insert ZapRegister after %s", inst);
                }
            }
            buffer.finish();
        }
    }
}
