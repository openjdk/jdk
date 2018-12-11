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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;
import org.junit.Assert;

import java.util.function.Predicate;

public abstract class MatchRuleTest extends GraalCompilerTest {
    private LIR lir;

    protected LIR getLIR() {
        return lir;
    }

    @Override
    protected LIRSuites createLIRSuites(OptionValues options) {
        LIRSuites suites = super.createLIRSuites(options);
        suites.getPreAllocationOptimizationStage().appendPhase(new CheckPhase());
        return suites;
    }

    public class CheckPhase extends LIRPhase<PreAllocationOptimizationContext> {
        @Override
        protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
            lir = lirGenRes.getLIR();
        }
    }

    protected void checkLIR(String methodName, Predicate<LIRInstruction> predicate, int expected) {
        compile(getResolvedJavaMethod(methodName), null);
        int actualOpNum = 0;
        for (LIRInstruction ins : lir.getLIRforBlock(lir.codeEmittingOrder()[0])) {
            if (predicate.test(ins)) {
                actualOpNum++;
            }
        }
        Assert.assertEquals(expected, actualOpNum);
    }

}
