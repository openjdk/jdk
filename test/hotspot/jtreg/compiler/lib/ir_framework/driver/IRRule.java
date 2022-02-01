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
import java.util.function.Consumer;

class IRRule {
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
        String[] counts = irAnno.counts();
        if (counts.length != 0) {
            try {
                return Counts.create(IRNode.mergeNodes(irAnno.counts()), this);
            } catch (TestFormatException e) {
                // Logged and reported later. Continue.
            }
        }
        return null;
    }

    private FailOn initFailOn(IR irAnno) {
        String[] failOn = irAnno.failOn();
        if (failOn.length != 0) {
            return new FailOn(IRNode.mergeNodes(failOn));
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

    public IRRuleMatchResult apply() {
        IRRuleMatchResult result = new IRRuleMatchResult(this);
        if (failOn != null) {
            apply(failOn, result, result::setFailOnFailures);
        }
        if (counts != null) {
            apply(counts, result, result::setCountsFailures);
        }
        return result;
    }

    private void apply(CheckAttribute check, IRRuleMatchResult result, Consumer<CheckAttributeMatchResult> setFailures) {
        CheckAttributeMatchResult checkResult = check.apply(irMethod.getOutput());
        if (checkResult.fail()) {
            setFailures.accept(checkResult);
            result.updateOutputMatch(getOutputMatch(check, checkResult));
        }
    }

    private OutputMatch getOutputMatch(CheckAttribute check, CheckAttributeMatchResult checkResult) {
        int totalMatches = checkResult.getMatchesCount();
        int idealFailuresCount = getMatchesCount(check, irMethod.getIdealOutput());
        int optoAssemblyFailuresCount = getMatchesCount(check, irMethod.getOptoAssemblyOutput());
        return findOutputMatch(totalMatches, idealFailuresCount, optoAssemblyFailuresCount);
    }


    private int getMatchesCount(CheckAttribute check, String compilation) {
        CheckAttributeMatchResult result = check.apply(compilation);
        return result.getMatchesCount();
    }

    /**
     * Compare different counts to find out, on what output a failure was matched.
     */
    private OutputMatch findOutputMatch(int totalMatches, int idealFailuresCount, int optoAssemblyFailuresCount) {
        if (someRegexMatchOnlyEntireOutput(totalMatches, idealFailuresCount, optoAssemblyFailuresCount) || anyMatchOnIdealAndOptoAssembly(idealFailuresCount, optoAssemblyFailuresCount)) {
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
