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
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parser.CountsAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parser.FailOnAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This class creates a list of {@link CompilePhaseIRRule} for each specified compile phase in {@link IR#phase()} of an
 * IR rule.
 *
 * @see CompilePhaseIRRule
 */
public class CompilePhaseIRRuleBuilder {
    private final List<RawConstraint> rawFailOnConstraints;
    private final List<RawConstraint> rawCountsConstraints;
    private final IRMethod irMethod;
    private final IR irAnno;

    private final List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();

    private CompilePhaseIRRuleBuilder(IRMethod irMethod, IR irAnno) {
        this.irMethod = irMethod;
        this.irAnno = irAnno;
        this.rawFailOnConstraints = new FailOnAttributeParser(irAnno.failOn()).parse();
        this.rawCountsConstraints = new CountsAttributeParser(irAnno.counts()).parse();
    }

    /**
     * Creates a list of {@link CompilePhaseIRRule} instances.
     */
    public static List<CompilePhaseIRRule> build(IRMethod irMethod, IR irAnno) {
        return new CompilePhaseIRRuleBuilder(irMethod, irAnno).build();
    }

    private List<CompilePhaseIRRule> build() {
        CompilePhase[] compilePhases = irAnno.phase();
        TestFormat.checkNoReport(new HashSet<>(List.of(compilePhases)).size() == compilePhases.length,
                                 "Cannot specify a compile phase twice");
        for (CompilePhase compilePhase : compilePhases) {
            if (compilePhase == CompilePhase.DEFAULT) {
                createCompilePhaseIRRulesForDefault();
            } else {
                createCompilePhaseIRRule(compilePhase);
            }
        }
        return compilePhaseIRRules;
    }

    private void createCompilePhaseIRRulesForDefault() {
        Map<CompilePhase, ConstraintLists> constraints = collectConstraintsForPhases();
        constraints.forEach((compilePhase, constraintLists) ->
                                    createCompilePhaseIRRule(constraintLists.getFailOnConstraints(),
                                                             constraintLists.getCountsConstraints(),
                                                             compilePhase));
    }

    private Map<CompilePhase, ConstraintLists> collectConstraintsForPhases() {
        List<Constraint> failOnConstraints = parseRawConstraints(rawFailOnConstraints, CompilePhase.DEFAULT);
        List<Constraint> countsConstraints = parseRawConstraints(rawCountsConstraints, CompilePhase.DEFAULT);
        Map<CompilePhase, ConstraintLists> constraintsMap = new HashMap<>();
        createConstraintLists(constraintsMap, failOnConstraints, true);
        createConstraintLists(constraintsMap, countsConstraints, false);
        return constraintsMap;
    }

    private void createConstraintLists(Map<CompilePhase, ConstraintLists> constraintsMap, List<Constraint> constraints,
                                       boolean isFailOn) {
        Map<CompilePhase, List<Constraint>> constraintsByCompilePhase =
                constraints.stream().collect(Collectors.groupingBy(Constraint::getCompilePhase));
        constraintsByCompilePhase.forEach((phase, constraintList) -> {
            ConstraintLists constraintLists = constraintsMap.get(phase);
            if (constraintLists == null) {
                constraintLists = new ConstraintLists();
                constraintsMap.put(phase, constraintLists);
            }
            if (isFailOn) {
                constraintLists.setFailOnConstraints(constraintList);
            } else {
                constraintLists.setCountsConstraints(constraintList);
            }
        });
    }

    private void createCompilePhaseIRRule(CompilePhase compilePhase) {
        List<Constraint> failOnConstraints = parseRawConstraints(rawFailOnConstraints, compilePhase);
        List<Constraint> countsConstraints = parseRawConstraints(rawCountsConstraints, compilePhase);
        createCompilePhaseIRRule(failOnConstraints, countsConstraints, compilePhase);
    }

    private void createCompilePhaseIRRule(List<Constraint> failOnConstraints, List<Constraint> countsConstraints,
                                          CompilePhase compilePhase) {
        String compilationOutput = irMethod.getOutput(compilePhase);
        if (compilationOutput.isEmpty()) {
            compilePhaseIRRules.add(new CompilePhaseNoCompilationIRRule(compilePhase));
        } else {
            FailOn failOn = failOnConstraints.isEmpty() ? null : new FailOn(failOnConstraints, compilationOutput);
            Counts counts = countsConstraints.isEmpty() ? null : new Counts(countsConstraints);
            compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, failOn, counts));
        }
    }

    private List<Constraint> parseRawConstraints(List<RawConstraint> rawConstraints,
                                                        CompilePhase compilePhase) {
        List<Constraint> constraintResultList = new ArrayList<>();
        for (RawConstraint rawConstraint : rawConstraints) {
            constraintResultList.add(rawConstraint.parse(compilePhase, irMethod));
        }
        return constraintResultList;
    }
}

