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

package compiler.lib.ir_framework.driver.irmatching.irmethod;

import compiler.lib.ir_framework.driver.irmatching.OutputMatch;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.util.List;

/**
 * Class to build the compilation output for an IR method.
 *
 * @see IRMethodMatchResult
 */
class MatchedCompilationOutputBuilder {
    private final IRMethod irMethod;
    private final OutputMatch outputMatch;

    public MatchedCompilationOutputBuilder(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        this.irMethod = irMethod;
        this.outputMatch = getOutputMatch(irRulesMatchResults);
    }

    private OutputMatch getOutputMatch(List<IRRuleMatchResult> irRulesMatchResults) {
        OutputMatch outputMatch;
        if (allMatchesOn(irRulesMatchResults, OutputMatch.IDEAL)) {
            outputMatch = OutputMatch.IDEAL;
        } else if (allMatchesOn(irRulesMatchResults, OutputMatch.OPTO_ASSEMBLY)) {
            outputMatch = OutputMatch.OPTO_ASSEMBLY;
        } else {
            outputMatch = OutputMatch.BOTH;
        }
        return outputMatch;
    }

    private boolean allMatchesOn(List<IRRuleMatchResult> irRulesMatchResults, OutputMatch outputMatch) {
        return irRulesMatchResults.stream().allMatch(r -> r.getOutputMatch() == outputMatch);
    }

    public String build() {
        StringBuilder builder = new StringBuilder();
        builder.append(getMethodLine());
        switch (outputMatch) {
            case IDEAL -> builder.append(irMethod.getIdealOutput());
            case OPTO_ASSEMBLY -> builder.append(irMethod.getOptoAssemblyOutput());
            case BOTH -> builder.append(irMethod.getOutput());
            default -> throw new TestFrameworkException("found unexpected OutputMatch " + outputMatch.name());
        }
        return builder.toString();
    }

    private String getMethodLine() {
        return ">>> Compilation of " + irMethod.getMethod() + ":" + System.lineSeparator();
    }
}
