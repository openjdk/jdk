/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.alloc.trace.lsra;

import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.alloc.Trace;
import org.graalvm.compiler.core.common.alloc.TraceBuilderResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.alloc.trace.lsra.TraceLinearScanPhase.TraceLinearScan;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.code.TargetDescription;

final class TraceLinearScanRegisterAllocationPhase extends TraceLinearScanAllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, Trace trace, MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig,
                    TraceBuilderResult traceBuilderResult, TraceLinearScan allocator) {
        allocateRegisters(allocator);
    }

    @SuppressWarnings("try")
    private static void allocateRegisters(TraceLinearScan allocator) {
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.logAndIndent("allocate registers")) {
            FixedInterval precoloredIntervals = allocator.createFixedUnhandledList();
            TraceInterval notPrecoloredIntervals = allocator.createUnhandledListByFrom(TraceLinearScanPhase.IS_VARIABLE_INTERVAL);

            // allocate cpu registers
            TraceLinearScanWalker lsw = new TraceLinearScanWalker(allocator, precoloredIntervals, notPrecoloredIntervals);
            lsw.walk();
            lsw.finishAllocation();
        }
    }

}
