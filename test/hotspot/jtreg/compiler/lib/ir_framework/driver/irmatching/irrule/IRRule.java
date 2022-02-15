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
import compiler.lib.ir_framework.driver.irmatching.CompilePhaseMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.*;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IRRule {
    private final IRMethod irMethod;
    private final int ruleId;
    private final IR irAnno;
    private final List<AbstractCompilePhaseIRRule> compilePhaseIRRules;

    public IRRule(IRMethod irMethod, int ruleId, IR irAnno) {
        this.irMethod = irMethod;
        this.ruleId = ruleId;
        this.irAnno = irAnno;
        List<FailOnNodeRegex> failOnNodeRegexes = initFailOnRegexes(irAnno.failOn());
        List<CountsNodeRegex> countsNodeRegexes = initCountsRegexes(irAnno.counts());
        this.compilePhaseIRRules = initPhaseIRRules(failOnNodeRegexes, countsNodeRegexes, irAnno.phase());
    }

    private List<AbstractCompilePhaseIRRule> initPhaseIRRules(List<FailOnNodeRegex> failOnNodeRegexes, List<CountsNodeRegex> countsNodeRegexes, CompilePhase[] compilePhases) {
        List<AbstractCompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();
        try {
            for (CompilePhase compilePhase : compilePhases) {
                compilePhaseIRRules.add(createPhaseIRRule(failOnNodeRegexes, countsNodeRegexes, compilePhase));
            }
        } catch (TestFormatException e) {
            reportFormatFailure(e);
        }
        return compilePhaseIRRules;
    }

    private void reportFormatFailure(TestFormatException e) {
        String postfixErrorMsg = " in count constraint for IR rule " + ruleId + " at " + getMethod();
        TestFormat.failNoThrow(e.getMessage() + postfixErrorMsg);
    }

    private AbstractCompilePhaseIRRule createPhaseIRRule(List<FailOnNodeRegex> failOnNodeRegexes, List<CountsNodeRegex> countsNodeRegexes, CompilePhase compilePhase) {
        if (compilePhase == CompilePhase.DEFAULT) {
            return new DefaultPhaseIRRule(irMethod, failOnNodeRegexes, countsNodeRegexes);
        } else {
            return new CompilePhaseIRRule(irMethod, compilePhase, failOnNodeRegexes, countsNodeRegexes);
        }
    }

    private List<FailOnNodeRegex> initFailOnRegexes(String[] failOnNodes) {
        if (failOnNodes != null) {
            FailOnParser failOnParser = new FailOnParser();
            return failOnParser.parseConstraint(failOnNodes);
        } else {
            return null;
        }
    }

    private List<CountsNodeRegex> initCountsRegexes(String[] countsNodes) {
        if (countsNodes != null) {
            CountsParser countsParser = new CountsParser();
            try {
                return countsParser.parseConstraint(countsNodes);
            } catch (TestFormatException e) {
                reportFormatFailure(e);
            }
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
        for (AbstractCompilePhaseIRRule compilePhaseIRRule : compilePhaseIRRules) {
            CompilePhaseMatchResult compilePhaseMatchResult = compilePhaseIRRule.applyCheckAttributes();
            if (compilePhaseMatchResult.fail()) {
                irRuleMatchResult.addCompilePhaseMatchResult(compilePhaseMatchResult);
            }
        }
        return irRuleMatchResult;
    }
}
