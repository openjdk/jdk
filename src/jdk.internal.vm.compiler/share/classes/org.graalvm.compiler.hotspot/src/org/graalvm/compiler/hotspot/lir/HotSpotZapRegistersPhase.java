/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.SaveRegistersOp;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapRegistersAfterInstruction;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool.ZapStackArgumentSpaceBeforeInstruction;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;
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
        boolean zapRegisters = stub != null && !stub.preservesRegisters();
        boolean zapStack = false;
        for (AllocatableValue arg : lirGenRes.getCallingConvention().getArguments()) {
            if (isStackSlot(arg)) {
                zapStack = true;
                break;
            }
        }
        if (zapRegisters || zapStack) {
            LIR lir = lirGenRes.getLIR();
            processLIR(context.diagnosticLirGenTool, (HotSpotLIRGenerationResult) lirGenRes, lir, zapRegisters, zapStack);
        }
    }

    private static void processLIR(DiagnosticLIRGeneratorTool diagnosticLirGenTool, HotSpotLIRGenerationResult res, LIR lir, boolean zapRegisters, boolean zapStack) {
        LIRInsertionBuffer buffer = new LIRInsertionBuffer();
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            if (block != null) {
                processBlock(diagnosticLirGenTool, res, lir, buffer, block, zapRegisters, zapStack);
            }
        }
    }

    @SuppressWarnings("try")
    private static void processBlock(DiagnosticLIRGeneratorTool diagnosticLirGenTool, HotSpotLIRGenerationResult res, LIR lir, LIRInsertionBuffer buffer, AbstractBlockBase<?> block,
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
                    LIRFrameState state = getLIRState(inst);
                    if (state != null) {
                        SaveRegistersOp zap = diagnosticLirGenTool.createZapRegisters();
                        SaveRegistersOp old = res.getCalleeSaveInfo().put(state, zap);
                        assert old == null : "Already another SaveRegisterOp registered! " + old;
                        buffer.append(index + 1, (LIRInstruction) zap);
                        debug.log("Insert ZapRegister after %s", inst);
                    }
                }
            }
            buffer.finish();
        }
    }

    /**
     * Returns the {@link LIRFrameState} of an instruction.
     */
    private static LIRFrameState getLIRState(LIRInstruction inst) {
        final LIRFrameState[] lirState = {null};
        inst.forEachState(state -> {
            assert lirState[0] == null : "Multiple states: " + inst;
            lirState[0] = state;
        });
        assert lirState[0] != null : "No state: " + inst;
        return lirState[0];
    }

}
