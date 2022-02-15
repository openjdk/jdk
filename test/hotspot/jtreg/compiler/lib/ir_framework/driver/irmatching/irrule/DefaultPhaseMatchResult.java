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
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.CompilePhaseMatchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This class represents an IR matching result of an IR rule applied to a compile phase.
 *
 * @see CheckAttributeMatchResult
 * @see IRRule
 */
public class DefaultPhaseMatchResult implements CompilePhaseMatchResult {
    List<NormalPhaseMatchResult> results = new ArrayList<>();

    @Override
    public boolean fail() {
        return !results.isEmpty();
    }

    @Override
    public CompilePhase getCompilePhase() {
        return CompilePhase.DEFAULT;
    }

    @Override
    public String getCompilationOutput() {
        return results.stream()
                      .map(NormalPhaseMatchResult::getCompilationOutput)
                      .collect(Collectors.joining(System.lineSeparator()));
    }

    public void addResult(NormalPhaseMatchResult result) {
        results.add(result);
    }

    public void addResultAndMerge(NormalPhaseMatchResult other) {
        CompilePhase compilePhase = other.getCompilePhase();
        TestFramework.check(compilePhase == CompilePhase.PRINT_IDEAL
                            || compilePhase == CompilePhase.PRINT_OPTO_ASSEMBLY, "cannot merge " + compilePhase);
        NormalPhaseMatchResult result = getMatchResultFor(compilePhase);
        if (result == null) {
            results.add(other);
        } else {
            result.mergeResults(other);
        }
    }

    /**
     * Since list has only few entries, it is acceptable to search by comparisons instead of using a map.
     */
    private NormalPhaseMatchResult getMatchResultFor(CompilePhase compilePhase) {
        return results.stream()
                      .filter(result -> result.getCompilePhase() == compilePhase)
                      .findAny()
                      .orElse(null);
    }

    @Override
    public String buildFailureMessage() {
        TestFramework.check(!results.isEmpty(), "must be non-empty");
        return results.stream().map(NormalPhaseMatchResult::buildFailureMessage).collect(Collectors.joining(""));
    }
}
