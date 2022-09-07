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
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRuleMatchResult;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class represents a {@link Test @Test} annotated method that has an associated non-empty list of applicable
 * {@link IR @IR} rules.
 *
 * @see IR
 * @see IRMethodMatchResult
 */
public class IRMethod implements Matchable {
    private final Method method;
    /**
     * Mapping from compile phase to compilation output found in hotspot_pid* file for that phase (if exist)
     */
    private final Map<CompilePhase, String> compilationOutputMap;
    private final List<IRRule> irRules;

    protected IRMethod(Method method) {
        this.method = method;
        this.irRules = new ArrayList<>();
        this.compilationOutputMap = null;
    }

    public IRMethod(Method method, int[] ruleIds, IR[] irAnnos, Map<CompilePhase, String> compilationOutputMap) {
        this.method = method;
        this.irRules = new ArrayList<>();
        this.compilationOutputMap = compilationOutputMap;
        createIRRules(method, ruleIds, irAnnos);
    }

    private void createIRRules(Method method, int[] ruleIds, IR[] irAnnos) {
        for (int ruleId : ruleIds) {
            try {
                irRules.add(new IRRule(this, ruleId, irAnnos[ruleId - 1]));
            } catch (TestFormatException e) {
                String postfixErrorMsg = " for IR rule " + ruleId + " at " + method + ".";
                TestFormat.failNoThrow(e.getMessage() + postfixErrorMsg);
            }
        }
    }

    public Method getMethod() {
        return method;
    }

    /**
     * Get the compilation output for non-default compile phase {@code phase} or an empty string if no output was found
     * in the hotspot_pid* file for this compile phase.
     */
    public String getOutput(CompilePhase phase) {
        TestFramework.check(phase != CompilePhase.DEFAULT, "cannot query for DEFAULT");
        return compilationOutputMap.getOrDefault(phase, "");
    }

    /**
     * Apply all IR rules of this method for each of the specified (or implied in case of
     * {@link CompilePhase#DEFAULT}) compile phases.
     */
    @Override
    public IRMethodMatchResult match() {
        TestFramework.check(!irRules.isEmpty(), "IRMethod cannot be created if there are no IR rules to apply");
        List<IRRuleMatchResult> results = new ArrayList<>();
        for (IRRule irRule : irRules) {
            IRRuleMatchResult result = irRule.match();
            if (result.fail()) {
                results.add(result);
            }
        }
        return new IRMethodMatchResult(this, results);
    }
}
