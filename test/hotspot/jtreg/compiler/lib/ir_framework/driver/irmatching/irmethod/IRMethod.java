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
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.MatchableMatcher;
import compiler.lib.ir_framework.driver.irmatching.irrule.IRRule;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.shared.TestFormat;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a {@link Test @Test} annotated method that has an associated non-empty list of applicable
 * {@link IR @IR} rules.
 *
 * @see IR
 * @see IRRule
 * @see IRMethodMatchResult
 */
public class IRMethod implements IRMethodMatchable {
    private final Method method;
    private final MatchableMatcher matcher;

    public IRMethod(Method method, int[] ruleIds, IR[] irAnnos, Compilation compilation, VMInfo vmInfo) {
        this.method = method;
        this.matcher = new MatchableMatcher(createIRRules(method, ruleIds, irAnnos, compilation, vmInfo));
    }

    private List<Matchable> createIRRules(Method method, int[] ruleIds, IR[] irAnnos, Compilation compilation, VMInfo vmInfo) {
        List<Matchable> irRules = new ArrayList<>();
        for (int ruleId : ruleIds) {
            try {
                irRules.add(new IRRule(ruleId, irAnnos[ruleId - 1], compilation, vmInfo));
            } catch (TestFormatException e) {
                String postfixErrorMsg = " for IR rule " + ruleId + " at " + method + ".";
                TestFormat.failNoThrow(e.getMessage() + postfixErrorMsg);
            }
        }
        return irRules;
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
        return new IRMethodMatchResult(method, matcher.match());
    }
}
