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

import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.OutputMatch;

/**
 * This class represents an IR matching result of an IR rule.
 *
 * @see CheckAttributeMatchResult
 * @see IRRule
 */
public class IRRuleMatchResult implements MatchResult {
    private final IRRule irRule;
    private CheckAttributeMatchResult failOnFailures = null;
    private CheckAttributeMatchResult countsFailures = null;
    private OutputMatch outputMatch;

    public IRRuleMatchResult(IRRule irRule) {
        this.irRule = irRule;
        this.outputMatch = OutputMatch.NONE;
    }

    private boolean hasFailOnFailures() {
        return failOnFailures != null;
    }

    public void setFailOnFailures(CheckAttributeMatchResult failOnFailures) {
        this.failOnFailures = failOnFailures;
    }

    private boolean hasCountsFailures() {
        return countsFailures != null;
    }

    public void setCountsFailures(CheckAttributeMatchResult countsFailures) {
        this.countsFailures = countsFailures;
    }

    public OutputMatch getOutputMatch() {
        return outputMatch;
    }

    @Override
    public boolean fail() {
        return failOnFailures != null || countsFailures != null;
    }

    public void updateOutputMatch(OutputMatch newOutputMatch) {
        TestFramework.check(newOutputMatch != OutputMatch.NONE, "must be valid state");
        switch (outputMatch) {
            case NONE -> outputMatch = newOutputMatch;
            case IDEAL -> outputMatch = newOutputMatch != OutputMatch.IDEAL
                    ? OutputMatch.BOTH : OutputMatch.IDEAL;
            case OPTO_ASSEMBLY -> outputMatch = newOutputMatch != OutputMatch.OPTO_ASSEMBLY
                    ? OutputMatch.BOTH : OutputMatch.OPTO_ASSEMBLY;
        }
    }

    /**
     * Build a failure message based on the collected failures of this object.
     */
    @Override
    public String buildFailureMessage() {
        StringBuilder failMsg = new StringBuilder();
        failMsg.append(getIRRuleLine());
        if (hasFailOnFailures()) {
            failMsg.append(failOnFailures.buildFailureMessage());
        }
        if (hasCountsFailures()) {
            failMsg.append(countsFailures.buildFailureMessage());
        }
        return failMsg.toString();
    }

    private String getIRRuleLine() {
        return "   * @IR rule " + irRule.getRuleId() + ": \"" + irRule.getIRAnno() + "\"" + System.lineSeparator();
    }
}
