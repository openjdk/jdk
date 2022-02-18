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


package compiler.lib.ir_framework.driver.irmatching.irrule.phase;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawCountsConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawCountsConstraintParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawFailOnConstraintParser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Class to build {@link CompilePhaseIRRule} instances for the default compile phase. It can create multiple instances
 * by splitting constraints according to occurrences of placeholder strings from {@link IRNode}.
 * <p>
 *
 * The following {@link CompilePhaseIRRule} instances are created:
 * <ul>
 *     <li><p>PrintIdeal: If the IR rule defines any constraint that uses a placeholder string from {@link IRNode}
 *            which can be replaced by {@link CompilePhase#PRINT_IDEAL} specific default regexes: Only keep these
 *            constraints for this {@link CompilePhaseIRRule} instance and filter out all other other constraints.</li>
 *     <li><p>PrintOptoAssembly: If the IR rule defines any constraint that uses a placeholder string from {@link IRNode}
 *            which can be replaced by {@link CompilePhase#PRINT_OPTO_ASSEMBLY} specific default regexes: Only keep these
 *            constraints for this {@link CompilePhaseIRRule} instance and filter out all other other constraints.</li>
 *     <li><p>Default: If the IR rule defines any constraint that does not use a placeholder string from {@link IRNode}
 *            which could be replaced by any default regexes: for this {@link CompilePhaseIRRule} instance and filter
 *            out all other other constraints.</li>
 * </ul>
 *
 * @see CompilePhaseIRRule
 * @see DefaultPhaseIRRule
 */
class DefaultPhaseIRRuleBuilder extends CompilePhaseIRRuleBuilder {

    public DefaultPhaseIRRuleBuilder(List<RawConstraint> rawFailOnConstraints, List<RawCountsConstraint> rawCountsConstraints, IRMethod irMethod) {
        super(rawFailOnConstraints, rawCountsConstraints, irMethod);
    }

    public List<CompilePhaseIRRule> create() {
        List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();
        addCompilePhaseIRRule(CompilePhase.PRINT_IDEAL, compilePhaseIRRules);
        addCompilePhaseIRRule(CompilePhase.PRINT_OPTO_ASSEMBLY, compilePhaseIRRules);
        addDefaultPhaseIRRule(compilePhaseIRRules);
        TestFramework.check(!compilePhaseIRRules.isEmpty(), "must create at least one object");
        return compilePhaseIRRules;
    }

    /**
     * Filter for PrintIdeal and PrintOptoAssembly and add to {@code compilePhaseIRRules} list.
     */
    private void addCompilePhaseIRRule(CompilePhase compilePhase, List<CompilePhaseIRRule> compilePhaseIRRules) {
        FailOn failOn = createFailOn(getFilteredFailOnConstraints(compilePhase), compilePhase);
        Counts counts = createCounts(getFilteredCountsConstraints(compilePhase), compilePhase);
        if (failOn != null || counts != null) {
            compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, failOn, counts));
        }
    }

    private List<Constraint> getFilteredFailOnConstraints(CompilePhase compilePhase) {
        List<RawConstraint> filteredList = filterDefaultConstraints(rawFailOnConstraints, compilePhase);
        return RawFailOnConstraintParser.parse(filteredList, compilePhase);
    }

    private List<CountsConstraint> getFilteredCountsConstraints(CompilePhase compilePhase) {
        List<RawCountsConstraint> filteredList = filterDefaultConstraints(rawCountsConstraints, compilePhase);
        return RawCountsConstraintParser.parse(filteredList, compilePhase);
    }

    private static <T extends RawConstraint> List<T> filterDefaultConstraints(List<T> rawConstraints, CompilePhase compilePhase) {
        return rawConstraints.stream()
                             .filter(r -> DefaultRegexes.getCompilePhaseForIRNode(r.getRawNodeString()) == compilePhase)
                             .collect(Collectors.toList());
    }

    /**
     * Filter for default phase (combined PrintIdeal and PrintOptoAssembly) and add to {@code compilePhaseIRRules} list.
     * We add additional PrintIdeal and PrintoOptoAssembly {@link CompilePhaseIRRule} instances in order to find out
     * which output that was matched when reporting failures on the default phase.
     */
    private void addDefaultPhaseIRRule(List<CompilePhaseIRRule> compilePhaseIRRules) {
        List<Constraint> failOnConstraints = getFilteredFailOnConstraints(CompilePhase.DEFAULT);
        List<CountsConstraint> countsConstraints = getFilteredCountsConstraints(CompilePhase.DEFAULT);
        FailOn failOn = createFailOn(failOnConstraints, CompilePhase.DEFAULT);
        Counts counts = createCounts(countsConstraints, CompilePhase.DEFAULT);
        if (failOn != null || counts != null) {
            compilePhaseIRRules.add(addDefaultPhaseIRRule(failOnConstraints, countsConstraints, failOn, counts));
        }
    }

    private DefaultPhaseIRRule addDefaultPhaseIRRule(List<Constraint> failOnConstraints, List<CountsConstraint> countsConstraints, FailOn failOn, Counts counts) {
        CompilePhaseIRRule idealIRRule = createCompilePhaseIRRule(CompilePhase.PRINT_IDEAL, failOnConstraints, countsConstraints);
        CompilePhaseIRRule optoAssemblyIRRule = createCompilePhaseIRRule(CompilePhase.PRINT_OPTO_ASSEMBLY, failOnConstraints, countsConstraints);
        return new DefaultPhaseIRRule(failOn, counts, idealIRRule, optoAssemblyIRRule);
    }

    private CompilePhaseIRRule createCompilePhaseIRRule(CompilePhase compilePhase, List<Constraint> failOnConstraints,
                                                        List<CountsConstraint> countsConstraints) {
        FailOn failOn = createFailOn(failOnConstraints, compilePhase);
        Counts counts = createCounts(countsConstraints, compilePhase);
        return new CompilePhaseIRRule(CompilePhase.DEFAULT, failOn, counts);
    }
}
