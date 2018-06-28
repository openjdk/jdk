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


package org.graalvm.compiler.hotspot.lir;

import java.util.EnumSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import jdk.vm.ci.meta.Value;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

/**
 * Checks that no registers exceed the MaxVectorSize flag from the VM config.
 */
public final class VerifyMaxRegisterSizePhase extends PostAllocationOptimizationPhase {

    private final int maxVectorSize;

    public VerifyMaxRegisterSizePhase(int maxVectorSize) {
        this.maxVectorSize = maxVectorSize;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR lir = lirGenRes.getLIR();
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            verifyBlock(lir, block);
        }
    }

    protected void verifyBlock(LIR lir, AbstractBlockBase<?> block) {
        for (LIRInstruction inst : lir.getLIRforBlock(block)) {
            verifyInstruction(inst);
        }
    }

    protected void verifyInstruction(LIRInstruction inst) {
        inst.visitEachInput(this::verifyOperands);
        inst.visitEachOutput(this::verifyOperands);
        inst.visitEachAlive(this::verifyOperands);
        inst.visitEachTemp(this::verifyOperands);
    }

    @SuppressWarnings("unused")
    protected void verifyOperands(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        if (isRegister(value)) {
            assert value.getPlatformKind().getSizeInBytes() <= maxVectorSize : "value " + value + " exceeds MaxVectorSize " + maxVectorSize;
        }
    }
}
