/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.ir_framework.driver.irmatching.irrule;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.shared.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class IRRule {
    private final IRMethod irMethod;
    private final int ruleId;
    private final IR irAnno;
    private final FailOn failOn;
    private final Counts counts;
    private final List<CompilePhase> compilePhases;

    public IRRule(IRMethod irMethod, int ruleId, IR irAnno) {
        this.irMethod = irMethod;
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        this.failOn = initFailOn(irAnno);
        this.counts = initCounts(irAnno);
        this.compilePhases = Arrays.asList(irAnno.phase());
    }

    private Counts initCounts(IR irAnno) {
        String[] countsConstraints = irAnno.counts();
        if (countsConstraints.length != 0) {
            try {
                return Counts.create(IRNode.mergeNodes(countsConstraints), this);
            } catch (TestFormatException e) {
                // Logged and reported later. Continue.
            }
        }
        return null;
    }

    private FailOn initFailOn(IR irAnno) {
        String[] failOnNodes = irAnno.failOn();
        if (failOnNodes.length != 0) {
            return new FailOn(IRNode.mergeNodes(failOnNodes));
        }
        return null;
    }

    public int getRuleId() {
        return ruleId;
    }

    public IR getIRAnno() {
        return irAnno;
    }

    public Method getMethod() {
        return irMethod.getMethod();
    }

    /**
     * Apply this IR rule by checking any failOn and counts attributes.
     */
    public IRRuleMatchResult applyCheckAttributesForPhases() {
        IRRuleMatchResult irRuleMatchResult = new IRRuleMatchResult(this);
        for (CompilePhase compilePhase : compilePhases) {
            CompilePhaseMatchResult compilePhaseMatchResult = applyCheckAttributes(compilePhase);
            if (compilePhaseMatchResult.fail()) {
                if (compilePhase == CompilePhase.DEFAULT) {
                    addDefaultResult(irRuleMatchResult, compilePhaseMatchResult);
                } else {
                    irRuleMatchResult.addCompilePhaseMatchResult(compilePhaseMatchResult);
                }
            }
        }
        return irRuleMatchResult;
    }

    /**
     * Report either PrintIdeal, PrintOpto or both if there is at least one match or
     */
    private void addDefaultResult(IRRuleMatchResult irRuleMatchResult, CompilePhaseMatchResult defaultMatchResult) {
        CompilePhaseMatchResult idealResult = applyCheckAttributes(CompilePhase.PRINT_IDEAL);
        CompilePhaseMatchResult optoAssemblyResult = applyCheckAttributes(CompilePhase.PRINT_OPTO_ASSEMBLY);
        DefaultMatchResultBuilder resultBuilder = new DefaultMatchResultBuilder(defaultMatchResult, idealResult,
                                                                                optoAssemblyResult);
        resultBuilder.createDefaultResult(irRuleMatchResult);
    }


    private CompilePhaseMatchResult applyCheckAttributes(CompilePhase compilePhase) {
        CompilePhaseMatchResult compilePhaseMatchResult = new CompilePhaseMatchResult(compilePhase);
        applyFailOn(compilePhase, compilePhaseMatchResult);
        applyCounts(compilePhase, compilePhaseMatchResult);
        return compilePhaseMatchResult;
    }

    private void applyFailOn(CompilePhase compilePhase, CompilePhaseMatchResult compilePhaseMatchResult) {
        if (failOn != null) {
            FailOnMatchResult matchResult = failOn.apply(irMethod.getOutput(compilePhase));
            if (matchResult.fail()) {
                compilePhaseMatchResult.setFailOnMatchResult(matchResult);
            }
        }
    }

    private void applyCounts(CompilePhase compilePhase, CompilePhaseMatchResult compilePhaseMatchResult) {
        if (counts != null) {
            CountsMatchResult matchResult = counts.apply(irMethod.getOutput(compilePhase));
            if (matchResult.fail()) {
                compilePhaseMatchResult.setCountsMatchResult(matchResult);
            }
        }
    }
}
