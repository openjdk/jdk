/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.profiling;

import java.util.ArrayList;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.lir.ConstantValue;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInsertionBuffer;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.StandardOp.BlockEndOp;
import org.graalvm.compiler.lir.StandardOp.LabelOp;
import org.graalvm.compiler.lir.gen.DiagnosticLIRGeneratorTool;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class MethodProfilingPhase extends PostAllocationOptimizationPhase {
    public static final String INVOCATION_GROUP = "METHOD_INVOCATION_COUNTER";
    public static final String ITERATION_GROUP = "METHOD_ITERATION_COUNTER";

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        new Analyzer(target, lirGenRes.getCompilationUnitName(), lirGenRes.getLIR(), context.diagnosticLirGenTool).run();
    }

    private class Analyzer {
        private final LIR lir;
        private final DiagnosticLIRGeneratorTool diagnosticLirGenTool;
        private final LIRInsertionBuffer buffer;
        private final String compilationUnitName;
        private final ConstantValue increment;

        Analyzer(TargetDescription target, String compilationUnitName, LIR lir, DiagnosticLIRGeneratorTool diagnosticLirGenTool) {
            this.lir = lir;
            this.compilationUnitName = compilationUnitName;
            this.diagnosticLirGenTool = diagnosticLirGenTool;
            this.buffer = new LIRInsertionBuffer();
            this.increment = new ConstantValue(LIRKind.fromJavaKind(target.arch, JavaKind.Int), JavaConstant.INT_1);
        }

        public void run() {
            // insert counter at method entry
            doBlock(lir.getControlFlowGraph().getStartBlock(), INVOCATION_GROUP);
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                if (block.isLoopHeader()) {
                    // insert counter at loop header
                    doBlock(block, ITERATION_GROUP);
                }
            }
        }

        public void doBlock(AbstractBlockBase<?> block, String group) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.size() >= 2 : "Malformed block: " + block + ", " + instructions;
            assert instructions.get(instructions.size() - 1) instanceof BlockEndOp : "Not a BlockEndOp: " + instructions.get(instructions.size() - 1);
            assert !(instructions.get(instructions.size() - 2) instanceof BlockEndOp) : "Is a BlockEndOp: " + instructions.get(instructions.size() - 2);
            assert instructions.get(0) instanceof LabelOp : "Not a LabelOp: " + instructions.get(0);
            assert !(instructions.get(1) instanceof LabelOp) : "Is a LabelOp: " + instructions.get(1);

            LIRInstruction op = diagnosticLirGenTool.createBenchmarkCounter(compilationUnitName, group, increment);
            buffer.init(instructions);
            buffer.append(1, op);
            buffer.finish();
        }
    }

}
