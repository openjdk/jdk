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
import compiler.lib.ir_framework.driver.irmatching.irrule.phase.CompilePhaseMatchResult;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to build the combined compilation output of all compile phases on which an IR rule failed.
 *
 * @see IRMethodMatchResult
 */
class MatchedCompilationOutputBuilder {

    public static String build(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        return buildMethodHeaderLine(irMethod) + buildMatchedCompileOutputOfPhases(irMethod, irRulesMatchResults);
    }

    private static String buildMethodHeaderLine(IRMethod irMethod) {
        return ">>> Compilation of " + irMethod.getMethod() + ":" + System.lineSeparator();
    }

    /**
     * Concat the compilation output of all failed compile phases with line breaks
     */
    private static String buildMatchedCompileOutputOfPhases(IRMethod irMethod, List<IRRuleMatchResult> irRulesMatchResults) {
        Set<CompilePhase> compilePhases = collectCompilePhases(irRulesMatchResults);
        return compilePhases.stream()
                            .map(irMethod::getOutput)
                            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    }

    /**
     * Return list of compile phases which resulted in an IR matching failure.
     */
    private static Set<CompilePhase> collectCompilePhases(List<IRRuleMatchResult> irRulesMatchResults) {
        return irRulesMatchResults
                .stream()
                // Stream<CompilePhaseMatchResult>
                .flatMap(irRuleMatchResult -> irRuleMatchResult.getCompilePhaseMatchResults().stream())
                .map(CompilePhaseMatchResult::getCompilePhase) // Stream<CompilePhase>
                .sorted(Enum::compareTo) // Keep order in which the compile phases are defined in the file
                .collect(Collectors.toCollection(LinkedHashSet::new)); // Filter duplicates
    }
}
