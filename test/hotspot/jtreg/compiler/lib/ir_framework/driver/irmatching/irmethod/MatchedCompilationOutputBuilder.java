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

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.CompilePhaseMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to build the compilation output for an IR method.
 *
 * @see IRMethodMatchResult
 */
class MatchedCompilationOutputBuilder {
    private final IRMethod irMethod;
    private final Set<CompilePhase> compilePhases;

    public MatchedCompilationOutputBuilder(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        this.irMethod = irMethod;
        this.compilePhases = collectCompilePhases(irRulesMatchResults);
    }

    private Set<CompilePhase> collectCompilePhases(List<IRRuleMatchResult> irRulesMatchResults) {
        return irRulesMatchResults
                .stream()
                // Stream<CompilePhaseMatchResult>
                .flatMap(irRuleMatchResult -> irRuleMatchResult.getCompilePhaseMatchResults().stream())
                .map(CompilePhaseMatchResult::getCompilePhase) // Stream<CompilePhase>
                .collect(Collectors.toSet()); // Filter duplicates
    }

    public String build() {
        return getMethodLine() + getOutputOfPhases();
    }

    private String getMethodLine() {
        return ">>> Compilation of " + irMethod.getMethod() + ":" + System.lineSeparator();
    }

    // Concat all phases with line breaks
    private String getOutputOfPhases() {
        return compilePhases
                .stream()
                .map(irMethod::getOutput)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }
}
