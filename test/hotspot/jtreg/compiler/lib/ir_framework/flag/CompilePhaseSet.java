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


package compiler.lib.ir_framework.flag;

import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.CountsAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.FailOnAttributeParser;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.parser.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.regexes.DefaultRegexes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Class that holds the current compile phase set while collecting phases in {@link CompilePhaseCollector}.
 *
 * @see CompilePhaseCollector
 */
class CompilePhaseSet {
    private final Set<CompilePhase> phases = new HashSet<>();
    private boolean checkIdeal = true;
    private boolean checkOptoAssembly = true;

    public Set<CompilePhase> getPhases() {
        return phases;
    }

    public void addCompilePhase(CompilePhase compilePhase) {
        phases.add(compilePhase);
    }

    public void addForDefault(IR irAnno) {
        addForFailOn(irAnno);
        addForCounts(irAnno);
    }

    private void addForFailOn(IR irAnno) {
        addForConstraint(irAnno.failOn(), FailOnAttributeParser::parse);
    }

    private void addForCounts(IR irAnno) {
        addForConstraint(irAnno.counts(), CountsAttributeParser::parse);
    }

    private void addForConstraint(String[] checkAttribute, Function<String[], List<? extends RawConstraint>> parseMethod) {
        if (checkAttribute.length > 0) {
            checkNonEmptyConstraint(checkAttribute, parseMethod);
        }
    }

    private void checkNonEmptyConstraint(String[] checkAttribute, Function<String[], List<? extends RawConstraint>> parseMethod) {
        if (checkIdeal || checkOptoAssembly) {
            List<? extends RawConstraint> constraints = parseMethod.apply(checkAttribute);
            if (hasConstraintsForPhase(constraints, CompilePhase.DEFAULT)) {
                addBoth();
            } else {
                checkIdealAndOpto(constraints);
            }
        }
    }

    private void addBoth() {
        addIdeal();
        addOptoAssembly();
    }

    private void addIdeal() {
        phases.add(CompilePhase.PRINT_IDEAL);
        checkIdeal = false;
    }

    private void addOptoAssembly() {
        phases.add(CompilePhase.PRINT_OPTO_ASSEMBLY);
        checkOptoAssembly = false;
    }

    private void checkIdealAndOpto(List<? extends RawConstraint> constraints) {
        if (hasConstraintsForPhase(constraints, CompilePhase.PRINT_IDEAL)) {
            addIdeal();
        }
        if (hasConstraintsForPhase(constraints, CompilePhase.PRINT_OPTO_ASSEMBLY)) {
            addOptoAssembly();
        }
    }

    private static <T extends RawConstraint> boolean hasConstraintsForPhase(List<T> rawConstraints, CompilePhase compilePhase) {
        return rawConstraints.stream().anyMatch(r -> DefaultRegexes.getCompilePhaseForIRNode(r.getRawNodeString()) == compilePhase);
    }
}
