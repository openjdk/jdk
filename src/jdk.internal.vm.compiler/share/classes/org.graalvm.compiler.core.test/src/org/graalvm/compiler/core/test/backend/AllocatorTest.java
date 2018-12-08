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


package org.graalvm.compiler.core.test.backend;

import java.util.HashSet;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.StandardOp.ValueMoveOp;
import org.graalvm.compiler.lir.ValueProcedure;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Assert;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.meta.Value;

public class AllocatorTest extends BackendTest {

    @SuppressWarnings("try")
    protected void testAllocation(String snippet, final int expectedRegisters, final int expectedRegRegMoves, final int expectedSpillMoves) {
        final StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        DebugContext debug = graph.getDebug();
        try (DebugContext.Scope s = debug.scope("AllocatorTest", graph, graph.method(), getCodeCache())) {
            final RegisterStats stats = new RegisterStats(getLIRGenerationResult(graph).getLIR());
            try (DebugContext.Scope s2 = debug.scope("Assertions", stats.lir)) {
                Assert.assertEquals("register count", expectedRegisters, stats.registers.size());
                Assert.assertEquals("reg-reg moves", expectedRegRegMoves, stats.regRegMoves);
                Assert.assertEquals("spill moves", expectedSpillMoves, stats.spillMoves);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private class RegisterStats {

        public final LIR lir;
        public HashSet<Register> registers = new HashSet<>();
        public int regRegMoves;
        public int spillMoves;

        RegisterStats(LIR lir) {
            this.lir = lir;

            for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
                if (block == null) {
                    continue;
                }
                for (LIRInstruction instr : lir.getLIRforBlock(block)) {
                    collectStats(instr);
                }
            }
        }

        private ValueProcedure collectStatsProc = (value, mode, flags) -> {
            if (ValueUtil.isRegister(value)) {
                final Register reg = ValueUtil.asRegister(value);
                registers.add(reg);
            }
            return value;
        };

        private void collectStats(final LIRInstruction instr) {
            instr.forEachOutput(collectStatsProc);

            if (ValueMoveOp.isValueMoveOp(instr)) {
                ValueMoveOp move = ValueMoveOp.asValueMoveOp(instr);
                Value def = move.getResult();
                Value use = move.getInput();
                if (ValueUtil.isRegister(def)) {
                    if (ValueUtil.isRegister(use)) {
                        regRegMoves++;
                    }
                } else if (LIRValueUtil.isStackSlotValue(def)) {
                    spillMoves++;
                }
            }
        }
    }
}
