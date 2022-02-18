/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.Matching;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class represents a {@link Test @Test} annotated method that has an associated non-empty list of applicable IR rules.
 *
 * @see IRRule
 * @see Test
 */
public class IRMethod implements Matching {
    private final Method method;
    private final Map<CompilePhase, String> compilationOutputMap;
    private String completeOutput;
    private final List<IRRule> irRules;

    public IRMethod(Method method, int[] ruleIds, IR[] irAnnos, Map<CompilePhase, String> compilationOutputMap) {
        this.method = method;
        this.irRules = new ArrayList<>();
        this.compilationOutputMap = compilationOutputMap;
        this.completeOutput = "";
        for (int ruleId : ruleIds) {
            try {
                irRules.add(new IRRule(this, ruleId, irAnnos[ruleId - 1]));
            } catch (TestFormatException e) {
                String postfixErrorMsg = " for IR rule " + ruleId + " at " + method;
                TestFormat.failNoThrow(e.getMessage() + postfixErrorMsg);
            }
        }
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Return the entire compilation output of all phases, regardless of IR matching failures.
     */
    public String getCompleteOutput() {
        if (completeOutput.isEmpty()) {
            completeOutput = createCompleteOutput();
        }
        return completeOutput;
    }

    private String createCompleteOutput() {
        String idealOutputs = compilationOutputMap.entrySet().stream()
                                                  .filter(e -> e.getKey() == CompilePhase.PRINT_OPTO_ASSEMBLY)
                                                  .map(Map.Entry::getValue)
                                                  .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        String optoAssemblyOutput = compilationOutputMap.get(CompilePhase.PRINT_OPTO_ASSEMBLY);
        if (optoAssemblyOutput != null) {
            // PrintOptoAssembly output is reported before the PrintIdeal output of PHASE_FINAL.
            // Put PrintOptoAssembly output last.
            return idealOutputs + System.lineSeparator() + System.lineSeparator() + optoAssemblyOutput;
        }
        return idealOutputs;
    }

    public String getOutput(CompilePhase phase) {
        return compilationOutputMap.get(phase);
    }

    /**
     * Apply all IR rules of this IR method on their specified compile phases.
     */
    @Override
    public IRMethodMatchResult match() {
        TestFramework.check(!irRules.isEmpty(), "IRMethod cannot be created if there are no IR rules to apply");
        List<IRRuleMatchResult> results = new ArrayList<>();
        if (getCompleteOutput().isEmpty()) {
            return new MissingCompilationResult(this, irRules.size());
        } else {
            return getNormalMatchResult(results);
        }
    }

    private NormalMatchResult getNormalMatchResult(List<IRRuleMatchResult> results) {
        for (IRRule irRule : irRules) {
            IRRuleMatchResult result = irRule.match();
            if (result.fail()) {
                results.add(result);
            }
        }
        return new NormalMatchResult(this, results);
    }
}
