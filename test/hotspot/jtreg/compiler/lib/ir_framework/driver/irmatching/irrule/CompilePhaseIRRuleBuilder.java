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
import compiler.lib.ir_framework.DefaultRegexes;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.parser.CountsNodeRegex;
import compiler.lib.ir_framework.driver.irmatching.parser.FailOnNodeRegex;
import compiler.lib.ir_framework.driver.irmatching.parser.NodeRegex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class representing a failOn attribute of an IR rule.
 *
 * @see IR#failOn()
 */
class CompilePhaseIRRuleBuilder {
    public static List<CompilePhaseIRRule> create(List<FailOnNodeRegex> failOnNodeRegexes, List<CountsNodeRegex> countsNodeRegexes,
                                                  CompilePhase compilePhase, IRMethod irMethod) {
        if (compilePhase != CompilePhase.DEFAULT) {
            return createForNormalPhase(failOnNodeRegexes, countsNodeRegexes, compilePhase, irMethod);
        } else {
            return createForDefaultPhase(failOnNodeRegexes, countsNodeRegexes, irMethod);
        }
    }

    private static List<CompilePhaseIRRule> createForNormalPhase(List<FailOnNodeRegex> failOnNodeRegexes,
                                                                 List<CountsNodeRegex> countsNodeRegexes,
                                                                 CompilePhase compilePhase, IRMethod irMethod) {
        FailOn failOn = FailOnNodeRegexParser.parse(failOnNodeRegexes, compilePhase);
        Counts counts = CountsNodeRegexParser.parse(countsNodeRegexes, compilePhase);
        return Collections.singletonList(new CompilePhaseIRRule(irMethod, compilePhase, failOn, counts));
    }

    private static List<CompilePhaseIRRule> createForDefaultPhase(List<FailOnNodeRegex> failOnNodeRegexes,
                                                                  List<CountsNodeRegex> countsNodeRegexes, IRMethod irMethod) {
        List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();
        addCompilePhaseIRRule(failOnNodeRegexes, countsNodeRegexes, CompilePhase.PRINT_IDEAL, irMethod, compilePhaseIRRules);
        addCompilePhaseIRRule(failOnNodeRegexes, countsNodeRegexes, CompilePhase.PRINT_OPTO_ASSEMBLY, irMethod, compilePhaseIRRules);
        addCompilePhaseIRRule(failOnNodeRegexes, countsNodeRegexes, CompilePhase.DEFAULT, irMethod, compilePhaseIRRules);
        TestFramework.check(!compilePhaseIRRules.isEmpty(), "must create at least one object");
        return compilePhaseIRRules;
    }

    private static void addCompilePhaseIRRule(List<FailOnNodeRegex> failOnNodeRegexes, List<CountsNodeRegex> countsNodeRegexes,
                                              CompilePhase compilePhase, IRMethod irMethod,
                                              List<CompilePhaseIRRule> compilePhaseIRRules) {
        FailOn failOn = createFailOn(failOnNodeRegexes, compilePhase);
        Counts counts = createCounts(countsNodeRegexes, compilePhase);
        if (failOn != null || counts != null) {
            if (compilePhase == CompilePhase.DEFAULT) {
                compilePhaseIRRules.add(new DefaultPhaseIRRule(irMethod, failOn, counts));
            } else {
                compilePhaseIRRules.add(new CompilePhaseIRRule(irMethod, compilePhase, failOn, counts));
            }
        }
    }

    private static FailOn createFailOn(List<FailOnNodeRegex> failOnNodeRegexes, CompilePhase compilePhase) {
        List<FailOnNodeRegex> filteredRegexes = DefaultRegexFilter.filter(failOnNodeRegexes, compilePhase);
        return FailOnNodeRegexParser.parse(filteredRegexes, compilePhase);
    }

    private static Counts createCounts(List<CountsNodeRegex> countsNodeRegexes, CompilePhase compilePhase) {
        List<CountsNodeRegex> filteredRegexes = DefaultRegexFilter.filter(countsNodeRegexes, compilePhase);
        return CountsNodeRegexParser.parse(filteredRegexes, compilePhase);
    }

    private static class DefaultRegexFilter {
        public static <T extends NodeRegex> List<T> filter(List<T> nodeRegexes, CompilePhase compilePhase) {
            return nodeRegexes.stream()
                              .filter(r -> DefaultRegexes.getCompilePhaseForIRNode(r.getRawNodeString()) == compilePhase)
                              .collect(Collectors.toList());
        }
    }
}
