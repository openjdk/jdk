/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.StandardOp.ImplicitNullCheck;
import org.graalvm.compiler.lir.StandardOp.NullCheck;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;

public final class NullCheckOptimizer extends PostAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR ir = lirGenRes.getLIR();
        AbstractBlockBase<?>[] blocks = ir.codeEmittingOrder();
        NullCheckOptimizer.foldNullChecks(ir, blocks, target.implicitNullCheckLimit);
    }

    private static void foldNullChecks(LIR ir, AbstractBlockBase<?>[] blocks, int implicitNullCheckLimit) {
        for (AbstractBlockBase<?> block : blocks) {
            if (block == null) {
                continue;
            }
            ArrayList<LIRInstruction> list = ir.getLIRforBlock(block);

            if (!list.isEmpty()) {

                LIRInstruction lastInstruction = list.get(0);
                for (int i = 0; i < list.size(); i++) {
                    LIRInstruction instruction = list.get(i);

                    if (instruction instanceof ImplicitNullCheck && lastInstruction instanceof NullCheck) {
                        NullCheck nullCheck = (NullCheck) lastInstruction;
                        ImplicitNullCheck implicitNullCheck = (ImplicitNullCheck) instruction;
                        if (implicitNullCheck.makeNullCheckFor(nullCheck.getCheckedValue(), nullCheck.getState(), implicitNullCheckLimit)) {
                            list.remove(i - 1);
                            if (i < list.size()) {
                                instruction = list.get(i);
                            }
                        }
                    }
                    lastInstruction = instruction;
                }
            }
        }
    }

}
