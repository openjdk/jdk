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
package org.graalvm.compiler.lir.alloc.lsra;

import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;

import jdk.vm.ci.code.TargetDescription;

public final class LinearScanRegisterAllocationPhase extends AllocationPhase {

    private final LinearScan allocator;

    LinearScanRegisterAllocationPhase(LinearScan allocator) {
        this.allocator = allocator;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        allocator.printIntervals("Before register allocation");
        allocateRegisters();
        allocator.printIntervals("After register allocation");
    }

    @SuppressWarnings("try")
    void allocateRegisters() {
        try (Indent indent = Debug.logAndIndent("allocate registers")) {
            Interval precoloredIntervals;
            Interval notPrecoloredIntervals;

            Interval.Pair result = allocator.createUnhandledLists(LinearScan.IS_PRECOLORED_INTERVAL, LinearScan.IS_VARIABLE_INTERVAL);
            precoloredIntervals = result.first;
            notPrecoloredIntervals = result.second;

            // allocate cpu registers
            LinearScanWalker lsw;
            if (OptimizingLinearScanWalker.Options.LSRAOptimization.getValue()) {
                lsw = new OptimizingLinearScanWalker(allocator, precoloredIntervals, notPrecoloredIntervals);
            } else {
                lsw = new LinearScanWalker(allocator, precoloredIntervals, notPrecoloredIntervals);
            }
            lsw.walk();
            lsw.finishAllocation();
        }
    }

}
