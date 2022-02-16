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
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawCountsConstraintParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawFailOnConstraintParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawCountsConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class representing a failOn attribute of an IR rule.
 *
 * @see IR#failOn()
 */
public class CompilePhaseIRRuleBuilder {
    public static List<CompilePhaseIRRule> create(List<RawConstraint> failOnRawConstraints, List<RawCountsConstraint> countsNodeRegexes,
                                                  CompilePhase compilePhase, IRMethod irMethod) {
        if (compilePhase != CompilePhase.DEFAULT) {
            return createForNormalPhase(failOnRawConstraints, countsNodeRegexes, compilePhase, irMethod);
        } else {
            return createForDefaultPhase(failOnRawConstraints, countsNodeRegexes, irMethod);
        }
    }

    private static List<CompilePhaseIRRule> createForNormalPhase(List<RawConstraint> failOnRawConstraints,
                                                                 List<RawCountsConstraint> countsNodeRegexes,
                                                                 CompilePhase compilePhase, IRMethod irMethod) {
        FailOn failOn = RawFailOnConstraintParser.parse(failOnRawConstraints, compilePhase);
        Counts counts = RawCountsConstraintParser.parse(countsNodeRegexes, compilePhase);
        return Collections.singletonList(new CompilePhaseIRRule(irMethod, compilePhase, failOn, counts));
    }

    private static List<CompilePhaseIRRule> createForDefaultPhase(List<RawConstraint> failOnRawConstraints,
                                                                  List<RawCountsConstraint> countsNodeRegexes, IRMethod irMethod) {
        List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();
        addCompilePhaseIRRule(failOnRawConstraints, countsNodeRegexes, CompilePhase.PRINT_IDEAL, irMethod, compilePhaseIRRules);
        addCompilePhaseIRRule(failOnRawConstraints, countsNodeRegexes, CompilePhase.PRINT_OPTO_ASSEMBLY, irMethod, compilePhaseIRRules);
        addCompilePhaseIRRule(failOnRawConstraints, countsNodeRegexes, CompilePhase.DEFAULT, irMethod, compilePhaseIRRules);
        TestFramework.check(!compilePhaseIRRules.isEmpty(), "must create at least one object");
        return compilePhaseIRRules;
    }

    private static void addCompilePhaseIRRule(List<RawConstraint> failOnRawConstraints, List<RawCountsConstraint> countsNodeRegexes,
                                              CompilePhase compilePhase, IRMethod irMethod,
                                              List<CompilePhaseIRRule> compilePhaseIRRules) {
        FailOn failOn = createFailOn(failOnRawConstraints, compilePhase);
        Counts counts = createCounts(countsNodeRegexes, compilePhase);
        if (failOn != null || counts != null) {
            if (compilePhase == CompilePhase.DEFAULT) {
                compilePhaseIRRules.add(new DefaultPhaseIRRule(irMethod, failOn, counts));
            } else {
                compilePhaseIRRules.add(new CompilePhaseIRRule(irMethod, compilePhase, failOn, counts));
            }
        }
    }

    private static FailOn createFailOn(List<RawConstraint> failOnRawConstraints, CompilePhase compilePhase) {
        List<RawConstraint> filteredRegexes = DefaultRegexFilter.filter(failOnRawConstraints, compilePhase);
        return RawFailOnConstraintParser.parse(filteredRegexes, compilePhase);
    }

    private static Counts createCounts(List<RawCountsConstraint> countsNodeRegexes, CompilePhase compilePhase) {
        List<RawCountsConstraint> filteredRegexes = DefaultRegexFilter.filter(countsNodeRegexes, compilePhase);
        return RawCountsConstraintParser.parse(filteredRegexes, compilePhase);
    }

    private static class DefaultRegexFilter {
        public static <T extends RawConstraint> List<T> filter(List<T> nodeRegexes, CompilePhase compilePhase) {
            return nodeRegexes.stream()
                              .filter(r -> DefaultRegexes.getCompilePhaseForIRNode(r.getRawNodeString()) == compilePhase)
                              .collect(Collectors.toList());
        }
    }
}
