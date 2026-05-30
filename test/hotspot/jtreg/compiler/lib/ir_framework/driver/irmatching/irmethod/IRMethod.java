/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.SkipIR;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.MatchableMatcher;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.network.testvm.java.IRRuleIds;
import compiler.lib.ir_framework.driver.network.testvm.java.VMInfo;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static compiler.lib.ir_framework.TestFramework.PRINT_RULE_MATCHING_TIME;

/**
 * This class represents a {@link Test @Test} annotated method that has an associated non-empty list of applicable
 * {@link IR @IR} rules.
 *
 * @see IR
 * @see IRRule
 * @see IRMethodMatchResult
 */
public class IRMethod implements IRMethodMatchable {
    private static final boolean IGNORE_SKIP_IR = Boolean.parseBoolean(System.getProperty("IgnoreSkipIR", "false"));

    private final Method method;
    private final Set<Integer> skippedIRRules;
    private final MatchableMatcher matcher;

    public IRMethod(Method method, IRRuleIds irRuleIds, IR[] irAnnos, Compilation compilation, VMInfo vmInfo) {
        this.method = method;
        this.skippedIRRules = createSkippedIRRules(irAnnos.length);
        this.matcher = new MatchableMatcher(createIRRules(method, irRuleIds, irAnnos, compilation, vmInfo));
    }

    private Set<Integer> createSkippedIRRules(int irAnnoCount) {
        SkipIR skipIR = method.getAnnotation(SkipIR.class);
        if (skipIR == null) {
            return Set.of();
        }

        if (IGNORE_SKIP_IR) {
            System.out.println("Matching @SkipIR-annotated method \"" + method.getName() + "\"");
            return Set.of();
        }

        int[] skipIRIndicesArray = skipIR.value();
        Set<Integer> skippedIRRules = createValidatedSkippedIRRules(irAnnoCount, skipIRIndicesArray);
        TestFormat.checkNoThrow(!skippedIRRules.isEmpty(), "Cannot specify empty @SkipIR annotation at " + method);
        TestFormat.checkNoThrow(skippedIRRules.size() == skipIRIndicesArray.length, "Found duplicated IR rule index in @SkipIR at " + method);
        return skippedIRRules;
    }

    private Set<Integer> createValidatedSkippedIRRules(int irAnnoCount, int[] skipIRIndicesArray) {
        return Arrays.stream(skipIRIndicesArray)
                .peek(skippedIndex -> checkValidRuleIndex(skippedIndex, irAnnoCount))
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }

    private void checkValidRuleIndex(int skippedIndex, int irAnnoCount) {
        TestFormat.checkNoThrow(skippedIndex > 0 && skippedIndex <= irAnnoCount, "Specified invalid IR rule index " + skippedIndex + " at " + method);
    }

    private List<Matchable> createIRRules(Method method, IRRuleIds irRuleIds, IR[] irAnnos, Compilation compilation, VMInfo vmInfo) {
        List<Matchable> irRules = new ArrayList<>();
        for (int ruleId : irRuleIds) {
            try {
                createIRRule(irAnnos, compilation, vmInfo, ruleId, irRules);
            } catch (TestFormatException e) {
                String postfixErrorMsg = " for IR rule " + ruleId + " at " + method + ".";
                TestFormat.failNoThrow(e.getMessage() + postfixErrorMsg);
            }
        }
        return irRules;
    }

    private void createIRRule(IR[] irAnnos, Compilation compilation, VMInfo vmInfo, int ruleId, List<Matchable> irRules) {
        if (shouldSkipIRRule(ruleId)) {
            return;
        }
        irRules.add(new IRRule(ruleId, irAnnos[ruleId - 1], compilation, vmInfo));
    }

    private boolean shouldSkipIRRule(int ruleId) {
        return skippedIRRules.contains(ruleId);
    }

    /**
     * Used only for sorting.
     */
    @Override
    public String name() {
        return method.getName();
    }

    /**
     * Apply all IR rules of this method for each of the specified (or implied in case of
     * {@link CompilePhase#DEFAULT}) compile phases.
     */
    @Override
    public MatchResult match() {
        if (!PRINT_RULE_MATCHING_TIME) {
            return new IRMethodMatchResult(method, matcher.match());
        }

        for (int i = 0; i < 10; i++) {  // warm up
            matcher.match();
        }

        long startTime = System.nanoTime();
        List<MatchResult> match = matcher.match();
        long endTime = System.nanoTime();
        long duration = (endTime - startTime);
        System.out.println("Verifying IR rules for " + name() + ": " + duration + " ns = " + (duration / 1_000_000) + " ms");
        return new IRMethodMatchResult(method, match);
    }
}
