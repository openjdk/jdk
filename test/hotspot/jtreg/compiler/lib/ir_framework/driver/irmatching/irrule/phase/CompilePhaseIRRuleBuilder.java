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
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.CountsConstraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base builder class to create a list of {@link CompilePhaseIRRule} for an IR rule to apply IR matching on.
 *
 * @see CompilePhaseIRRule
 */
public class CompilePhaseIRRuleBuilder {
    private final List<RawConstraint> rawFailOnConstraints;
    private final List<RawCountsConstraint> rawCountsConstraints;
    private final IRMethod irMethod;
    private final IR irAnno;
    private final List<CompilePhaseIRRule> compilePhaseIRRules = new ArrayList<>();

    private CompilePhaseIRRuleBuilder(IRMethod irMethod, IR irAnno) {
        this.irMethod = irMethod;
        this.irAnno = irAnno;
        this.rawFailOnConstraints = FailOnAttributeParser.parse(irAnno.failOn());
        this.rawCountsConstraints = CountsAttributeParser.parse(irAnno.counts());
    }

    /**
     * Creates a list of {@link CompilePhaseIRRule} instances.
     */
    public static List<CompilePhaseIRRule> build(IRMethod irMethod, IR irAnno) {
        return new CompilePhaseIRRuleBuilder(irMethod, irAnno).build();
    }

    private List<CompilePhaseIRRule> build() {
        for (CompilePhase compilePhase : irAnno.phase()) {
            if (compilePhase == CompilePhase.DEFAULT) {
                createCompilePhaseIRRulesForDefault();
            } else {
                createCompilePhaseIRRule(compilePhase);
            }
        }
        sortByPhases();
        return compilePhaseIRRules;
    }

    private void createCompilePhaseIRRulesForDefault() {
        Map<CompilePhase, ConstraintLists> map = new HashMap<>();
        collectFailOnConstraints(map);
        collectCountsConstraints(map);
        createCompilePhaseIRRules(map);
    }

    private void collectFailOnConstraints(Map<CompilePhase, ConstraintLists> map) {
        List<Constraint> failOnConstraints = RawFailOnConstraintParser.parse(rawFailOnConstraints, CompilePhase.DEFAULT);
        if (failOnConstraints != null) {
            Map<CompilePhase, List<Constraint>> collect = failOnConstraints.stream().collect(Collectors.groupingBy(Constraint::getCompilePhase));
            collect.forEach((phase, constraintsList) -> map.put(phase, ConstraintLists.createWithFailOnConstraints(constraintsList)));
        }
    }

    private void collectCountsConstraints(Map<CompilePhase, ConstraintLists> map) {
        List<CountsConstraint> countsConstraints = RawCountsConstraintParser.parse(rawCountsConstraints, CompilePhase.DEFAULT);
        if (countsConstraints != null) {
            Map<CompilePhase, List<CountsConstraint>> collect = countsConstraints.stream().collect(Collectors.groupingBy(Constraint::getCompilePhase));
            collect.forEach((phase, constraintsList) -> {
                ConstraintLists constraintLists = map.get(phase);
                if (constraintLists != null) {
                    constraintLists.setCountsConstraints(constraintsList);
                } else {
                    map.put(phase, ConstraintLists.createWithCountsConstraints(constraintsList));
                }
            });
        }
    }

    private void createCompilePhaseIRRules(Map<CompilePhase, ConstraintLists> map) {
        map.forEach((phase, constraintLists) -> {
            FailOn failOn = createFailOn(constraintLists.getFailOnConstraints());
            Counts counts = createCounts(constraintLists.getCountsConstraints());
            compilePhaseIRRules.add(new CompilePhaseIRRule(phase, failOn, counts, irMethod.getOutput(phase)));
        });
    }


    private FailOn createFailOn(List<Constraint> constraintsList) {
        if (constraintsList != null) {
            return new FailOn(constraintsList);
        } else {
            return null;
        }
    }

    private Counts createCounts(List<CountsConstraint> constraintList) {
        if (constraintList != null) {
            return new Counts(constraintList);
        } else {
            return null;
        }
    }

    private void createCompilePhaseIRRule(CompilePhase compilePhase) {
        List<Constraint> failOnConstraints = RawFailOnConstraintParser.parse(rawFailOnConstraints, compilePhase);
        FailOn failOn = createFailOn(failOnConstraints);
        List<CountsConstraint> countsConstraints = RawCountsConstraintParser.parse(rawCountsConstraints, compilePhase);
        Counts counts = createCounts(countsConstraints);
        compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, failOn, counts, irMethod.getOutput(compilePhase)));
    }

    private void sortByPhases() {
        compilePhaseIRRules.sort(Comparator.comparingInt(r -> r.compilePhase.ordinal()));
    }
}

