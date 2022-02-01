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

import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;

import java.util.List;

/**
 * This class represents an IR matching result of all IR rules of a method.
 *
 * @see IRRuleMatchResult
 * @see IRMethod
 */
public class IRMethodMatchResult implements Comparable<IRMethodMatchResult>, MatchResult {
    private final IRMethod irMethod;
    private final List<IRRuleMatchResult> irRulesMatchResults;
    private final FailureMessageBuilder failureMessageBuilder;
    private final MatchedCompilationBuilder matchedCompilationBuilder;

    IRMethodMatchResult(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        this.irMethod = irMethod;
        this.irRulesMatchResults = irRulesMatchResults;
        boolean missingCompilationOutput = irMethod.getOutput().isEmpty();
        this.failureMessageBuilder = new FailureMessageBuilder(irMethod, irRulesMatchResults, missingCompilationOutput);
        this.matchedCompilationBuilder = new MatchedCompilationBuilder(irMethod, irRulesMatchResults, missingCompilationOutput);
    }

    public List<IRRuleMatchResult> getIrRulesMatchResults() {
        return irRulesMatchResults;
    }

    public boolean fail() {
        return !irRulesMatchResults.isEmpty();
    }

    public String getMatchedCompilation() {
        return matchedCompilationBuilder.build();
    }

    @Override
    public String buildFailureMessage() {
        return failureMessageBuilder.build();
    }

    public int getFailedIRRuleCount() {
        return irRulesMatchResults.size();
    }

    @Override
    public int compareTo(IRMethodMatchResult other) {
        return this.irMethod.getMethod().getName().compareTo(other.irMethod.getMethod().getName());
    }
}
