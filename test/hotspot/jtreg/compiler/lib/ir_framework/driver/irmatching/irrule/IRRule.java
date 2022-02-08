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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.OutputMatch;
import compiler.lib.ir_framework.shared.*;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public class IRRule {
    private final IRMethod irMethod;
    private final int ruleId;
    private final IR irAnno;
    private final FailOn failOn;
    private final Counts counts;

    public IRRule(IRMethod irMethod, int ruleId, IR irAnno) {
        this.irMethod = irMethod;
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        this.failOn = initFailOn(irAnno);
        this.counts = initCounts(irAnno);
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
    public IRRuleMatchResult applyCheckAttribute() {
        IRRuleMatchResult result = new IRRuleMatchResult(this);
        if (failOn != null) {
            applyCheckAttribute(failOn, result, result::setFailOnFailures);
        }
        if (counts != null) {
            applyCheckAttribute(counts, result, result::setCountsFailures);
        }
        return result;
    }

    private void applyCheckAttribute(CheckAttribute checkAttribute, IRRuleMatchResult result,
                                     Consumer<CheckAttributeMatchResult> setFailures) {
        CheckAttributeMatchResult checkAttributeResult = checkAttribute.apply(irMethod.getOutput());
        if (checkAttributeResult.fail()) {
            setFailures.accept(checkAttributeResult);
            result.updateOutputMatch(getOutputMatch(checkAttribute, checkAttributeResult));
        }
    }

    /**
     * Determine how the output was matched by reapplying the check attribute for the PrintIdeal and PrintOptoAssembly
     * output separately.
     */
    private OutputMatch getOutputMatch(CheckAttribute checkAttribute, CheckAttributeMatchResult checkAttributeResult) {
        int totalMatches = checkAttributeResult.getMatchesCount();
        int idealFailuresCount = getMatchesCount(checkAttribute, irMethod.getIdealOutput());
        int optoAssemblyFailuresCount = getMatchesCount(checkAttribute, irMethod.getOptoAssemblyOutput());
        return findOutputMatch(totalMatches, idealFailuresCount, optoAssemblyFailuresCount);
    }

    private int getMatchesCount(CheckAttribute checkAttribute, String compilation) {
        CheckAttributeMatchResult result = checkAttribute.apply(compilation);
        return result.getMatchesCount();
    }

    /**
     * Compare different counts to find out, on what output a failure was matched.
     */
    private OutputMatch findOutputMatch(int totalMatches, int idealFailuresCount, int optoAssemblyFailuresCount) {
        if (totalMatches == 0
            || someRegexMatchOnlyEntireOutput(totalMatches, idealFailuresCount, optoAssemblyFailuresCount)
            || anyMatchOnIdealAndOptoAssembly(idealFailuresCount, optoAssemblyFailuresCount)) {
            return OutputMatch.BOTH;
        } else if (optoAssemblyFailuresCount == 0) {
            return OutputMatch.IDEAL;
        } else {
            return OutputMatch.OPTO_ASSEMBLY;
        }
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
}
