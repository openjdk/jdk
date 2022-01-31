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

package compiler.lib.ir_framework.driver;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.shared.*;

import java.lang.reflect.Method;
import java.util.List;

class IRRule {
    private final IRMethod irMethod;
    private final int ruleId;
    private final IR irAnno;
    private FailOn failOn;
    private Counts counts;

    public IRRule(IRMethod irMethod, int ruleId, IR irAnno) {
        this.irMethod = irMethod;
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        String[] failOn = irAnno.failOn();
        if (failOn.length != 0) {
            this.failOn = new FailOn(IRNode.mergeNodes(failOn));
        }
        String[] counts = irAnno.counts();
        if (counts.length != 0) {
            try {
                this.counts = Counts.create(IRNode.mergeNodes(irAnno.counts()), this);
            } catch (TestFormatException e) {
                // Logged and reported later. Continue.
            }
        }
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

    public IRRuleMatchResult apply() {
        IRRuleMatchResult result = new IRRuleMatchResult(this);
        applyFailOn(result);
        applyCounts(result);
        return result;
    }

    private void applyFailOn(IRRuleMatchResult result) {
        List<? extends Failure> failures = apply(failOn, irMethod.getOutput());
        updateFailOnResult(result, failures);
    }

    private void updateFailOnResult(IRRuleMatchResult result, List<? extends Failure> failures) {
        if (!failures.isEmpty()) {
            result.setFailOnFailures(failures);
            setWhichOutputMatched(failOn, result, failures);
        }
    }

    private void applyCounts(IRRuleMatchResult result) {
        List<? extends Failure> failures = apply(counts, irMethod.getOutput());
        updateCountsResult(result, failures);
    }

    private void updateCountsResult(IRRuleMatchResult result, List<? extends Failure> failures) {
        if (!failures.isEmpty()) {
            result.setCountsFailures(failures);
            setWhichOutputMatched(counts, result, failures);
        }
    }

    private void updateResultHowMatched(IRRuleMatchResult result, int totalMatches, int idealFailuresCount, int optoAssemblyFailuresCount) {
        if (someRegexMatchOnlyEntireOutput(totalMatches, idealFailuresCount, optoAssemblyFailuresCount) || anyMatchOnIdealAndOptoAssembly(idealFailuresCount, optoAssemblyFailuresCount)) {
            result.setIdealMatch();
            result.setOptoAssemblyMatch();
        } else if (optoAssemblyFailuresCount == 0) {
            result.setIdealMatch();
        } else {
            result.setOptoAssemblyMatch();
        }
    }

    private void setWhichOutputMatched(CheckAttribute check, IRRuleMatchResult result, List<? extends Failure> failures) {
        int totalMatches = failures.stream().map(Failure::getMatchesCount).reduce(0, Integer::sum);
        int idealFailuresCount = getFailureCounts(check, irMethod.getIdealOutput());
        int optoAssemblyFailuresCount = getFailureCounts(check, irMethod.getOptoAssemblyOutput());
        updateResultHowMatched(result, totalMatches, idealFailuresCount, optoAssemblyFailuresCount);
    }


    private int getFailureCounts(CheckAttribute check, String compilation) {
        List<? extends Failure> failures = apply(check, compilation);
        return failures.stream().map(Failure::getMatchesCount).reduce(0, Integer::sum);
    }


    /**
     * Do we have a regex that is only matched on the entire ideal + opto assembly output?
     */
    private boolean someRegexMatchOnlyEntireOutput(int totalCount, int idealFailuresCount, int optoAssemblyFailuresCount) {
        return totalCount != idealFailuresCount + optoAssemblyFailuresCount;
    }

    /**
     * Do we have a match on ideal and opto assembly for this rule?
     */
    private boolean anyMatchOnIdealAndOptoAssembly(int idealFailuresCount, int optoAssemblyFailuresCount) {
        return idealFailuresCount > 0 && optoAssemblyFailuresCount > 0;
    }

    private List<? extends Failure> apply(CheckAttribute checkAttribute, String compilation) {
        if (checkAttribute != null) {
            return checkAttribute.apply(compilation);
        }
        return Failure.NO_FAILURE;
    }
}
