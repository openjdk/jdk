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
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawCheckAttribute;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    private final List<Matchable> compilePhaseIRRules = new ArrayList<>();

    private CompilePhaseIRRuleBuilder(IRMethod irMethod, IR irAnno) {
        this.irMethod = irMethod;
        this.irAnno = irAnno;
        this.rawFailOnConstraints = RawCheckAttribute.createFailOn(irAnno).parse();
        this.rawCountsConstraints = RawCheckAttribute.createCounts(irAnno).parse();
    }

    /**
     * Creates a list of {@link CompilePhaseIRRule} instances.
     */
    public static List<Matchable> build(IRMethod irMethod, IR irAnno) {
        return new CompilePhaseIRRuleBuilder(irMethod, irAnno).build();
    }

    private List<Matchable> build() {
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
        DefaultPhaseRawConstraintParser parser = new DefaultPhaseRawConstraintParser(irMethod);
        Map<CompilePhase, List<Matchable>> matchablesForCompilePhase = parser.parse(rawFailOnConstraints, rawCountsConstraints);
        matchablesForCompilePhase.forEach((compilePhase, constraints) -> {
            if (irMethod.getOutput(compilePhase).isEmpty()) {
                compilePhaseIRRules.add(new CompilePhaseNoCompilationIRRule(compilePhase));
            } else {
                compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, constraints));
            }
        });
    }

    private void createCompilePhaseIRRule(CompilePhase compilePhase) {
        List<Constraint> failOnConstraints = parseRawConstraints(rawFailOnConstraints, compilePhase);
        List<Constraint> countsConstraints = parseRawConstraints(rawCountsConstraints, compilePhase);
        if (irMethod.getOutput(compilePhase).isEmpty()) {
            compilePhaseIRRules.add(new CompilePhaseNoCompilationIRRule(compilePhase));
        } else {
            createValidCompilePhaseIRRule(compilePhase, failOnConstraints, countsConstraints);
        }
    }

    private void createValidCompilePhaseIRRule(CompilePhase compilePhase, List<Constraint> failOnConstraints,
                                               List<Constraint> countsConstraints) {
        String compilationOutput = irMethod.getOutput(compilePhase);
        List<Matchable> checkAttributes = new ArrayList<>();
        if (!failOnConstraints.isEmpty()) {
            checkAttributes.add(new FailOn(failOnConstraints, compilationOutput));
        }

        if (!countsConstraints.isEmpty()) {
            checkAttributes.add(new Counts(countsConstraints, compilationOutput));
        }
        compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, checkAttributes));
    }

    private List<Constraint> parseRawConstraints(List<RawConstraint> rawConstraints,
                                                 CompilePhase compilePhase) {
        List<Constraint> constraintResultList = new ArrayList<>();
        for (RawConstraint rawConstraint : rawConstraints) {
            constraintResultList.add(rawConstraint.parse(compilePhase));
        }
        return constraintResultList;
    }
}

