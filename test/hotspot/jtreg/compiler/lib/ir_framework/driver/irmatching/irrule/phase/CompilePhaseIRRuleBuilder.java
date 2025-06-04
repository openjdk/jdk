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
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawCounts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.parsing.RawFailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.shared.TestFormat;

import java.util.*;

/**
 * This class creates a list of {@link CompilePhaseIRRule} for each specified compile phase in {@link IR#phase()} of an
 * IR rule. Default compile phases of {@link IRNode} placeholder strings as found in {@link RawConstraint} objects are
 * replaced by the actual default phases. The resulting parsed {@link Constraint} objects which now belong to a
 * non-default compile phase are moved to the check attribute matchables which represent these compile phases.
 *
 * @see CompilePhaseIRRule
 */
public class CompilePhaseIRRuleBuilder {
    private final IR irAnno;
    private final List<RawConstraint> rawFailOnConstraints;
    private final List<RawConstraint> rawCountsConstraints;
    private final Compilation compilation;
    private final SortedSet<CompilePhaseIRRuleMatchable> compilePhaseIRRules = new TreeSet<>();

    public CompilePhaseIRRuleBuilder(IR irAnno, Compilation compilation) {
        this.irAnno = irAnno;
        this.compilation = compilation;
        this.rawFailOnConstraints = new RawFailOn(irAnno.failOn()).createRawConstraints();
        this.rawCountsConstraints = new RawCounts(irAnno.counts()).createRawConstraints();
    }

    public SortedSet<CompilePhaseIRRuleMatchable> build(VMInfo vmInfo) {
        CompilePhase[] compilePhases = irAnno.phase();
        TestFormat.checkNoReport(new HashSet<>(List.of(compilePhases)).size() == compilePhases.length,
                                 "Cannot specify a compile phase twice");
        for (CompilePhase compilePhase : compilePhases) {
            if (compilePhase == CompilePhase.DEFAULT) {
                createCompilePhaseIRRulesForDefault(vmInfo);
            } else {
                createCompilePhaseIRRule(compilePhase, vmInfo);
            }
        }
        return compilePhaseIRRules;
    }

    private void createCompilePhaseIRRulesForDefault(VMInfo vmInfo) {
        DefaultPhaseRawConstraintParser parser = new DefaultPhaseRawConstraintParser(compilation);
        Map<CompilePhase, List<Matchable>> checkAttributesForCompilePhase =
                parser.parse(rawFailOnConstraints, rawCountsConstraints, vmInfo);
        checkAttributesForCompilePhase.forEach((compilePhase, constraints) -> {
            if (compilation.hasOutput(compilePhase)) {
                compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, constraints,
                                                               compilation.output(compilePhase)));
            } else {
                compilePhaseIRRules.add(new CompilePhaseNoCompilationIRRule(compilePhase));
            }
        });
    }

    private void createCompilePhaseIRRule(CompilePhase compilePhase, VMInfo vmInfo) {
        List<Constraint> failOnConstraints = parseRawConstraints(rawFailOnConstraints, compilePhase, vmInfo);
        List<Constraint> countsConstraints = parseRawConstraints(rawCountsConstraints, compilePhase, vmInfo);
        if (compilation.hasOutput(compilePhase)) {
            createValidCompilePhaseIRRule(compilePhase, failOnConstraints, countsConstraints);
        } else {
            compilePhaseIRRules.add(new CompilePhaseNoCompilationIRRule(compilePhase));
        }
    }

    private void createValidCompilePhaseIRRule(CompilePhase compilePhase, List<Constraint> failOnConstraints,
                                               List<Constraint> countsConstraints) {
        String compilationOutput = compilation.output(compilePhase);
        List<Matchable> checkAttributes = new ArrayList<>();
        if (!failOnConstraints.isEmpty()) {
            checkAttributes.add(new FailOn(failOnConstraints, compilationOutput));
        }

        if (!countsConstraints.isEmpty()) {
            checkAttributes.add(new Counts(countsConstraints));
        }
        compilePhaseIRRules.add(new CompilePhaseIRRule(compilePhase, checkAttributes, compilation.output(compilePhase)));
    }

    private List<Constraint> parseRawConstraints(List<RawConstraint> rawConstraints,
                                                 CompilePhase compilePhase,
                                                 VMInfo vmInfo) {
        List<Constraint> constraintResultList = new ArrayList<>();
        for (RawConstraint rawConstraint : rawConstraints) {
            constraintResultList.add(rawConstraint.parse(compilePhase, compilation.output(compilePhase), vmInfo));
        }
        return constraintResultList;
    }
}
