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
import compiler.lib.ir_framework.driver.irmatching.Compilation;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.CheckAttributeType;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;
import compiler.lib.ir_framework.driver.irmatching.parser.VMInfo;
import compiler.lib.ir_framework.shared.TestFrameworkException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class creates {@link FailOn} and {@link Counts} objects for the default compile phase by parsing the raw
 * constraints. Each {@link RawConstraint} has a well-defined default phase. This class collects all parsed
 * {@link Constraint} objects for a compile phase in order to create {@link FailOn} and {@link Counts} objects with them.
 */
class DefaultPhaseRawConstraintParser {
    private final Compilation compilation;

    public DefaultPhaseRawConstraintParser(Compilation compilation) {
        this.compilation = compilation;
    }

    public Map<CompilePhase, List<Matchable>> parse(List<RawConstraint> rawFailOnConstraints,
                                                    List<RawConstraint> rawCountsConstraints,
                                                    VMInfo vmInfo) {
        Map<CompilePhase, Matchable> failOnForCompilePhase = parseRawConstraints(rawFailOnConstraints,
                                                                                 CheckAttributeType.FAIL_ON,
                                                                                 vmInfo);
        Map<CompilePhase, Matchable> countsForCompilePhase = parseRawConstraints(rawCountsConstraints,
                                                                                 CheckAttributeType.COUNTS,
                                                                                 vmInfo);
        return mergeCheckAttributesForCompilePhase(failOnForCompilePhase, countsForCompilePhase);
    }

    private Map<CompilePhase, Matchable> parseRawConstraints(List<RawConstraint> rawConstraints,
                                                             CheckAttributeType checkAttributeType,
                                                             VMInfo vmInfo) {
        Map<CompilePhase, List<Constraint>> matchableForCompilePhase = new HashMap<>();
        for (RawConstraint rawConstraint : rawConstraints) {
            CompilePhase compilePhase = rawConstraint.defaultCompilePhase();
            List<Constraint> checkAttribute =
                    matchableForCompilePhase.computeIfAbsent(compilePhase, k -> new ArrayList<>());
            checkAttribute.add(rawConstraint.parse(compilePhase, compilation.output(compilePhase), vmInfo));
        }
        return replaceConstraintsWithCheckAttribute(matchableForCompilePhase, checkAttributeType);
    }

    private Map<CompilePhase, Matchable>
    replaceConstraintsWithCheckAttribute(Map<CompilePhase, List<Constraint>> matchableForCompilePhase,
                                         CheckAttributeType checkAttributeType) {
        return matchableForCompilePhase
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                                          entry -> createCheckAttribute(checkAttributeType,
                                                                        entry.getKey(), entry.getValue())));
    }

    private Matchable createCheckAttribute(CheckAttributeType checkAttributeType, CompilePhase compilePhase,
                                           List<Constraint> constraints) {
        switch (checkAttributeType) {
            case FAIL_ON -> {
                return new FailOn(constraints, compilation.output(compilePhase));
            }
            case COUNTS -> {
                return new Counts(constraints);
            }
            default -> throw new TestFrameworkException("unsupported: " + checkAttributeType);
        }
    }

    private static Map<CompilePhase, List<Matchable>>
    mergeCheckAttributesForCompilePhase(Map<CompilePhase, Matchable> failOnForCompilePhase,
                                        Map<CompilePhase, Matchable> countsForCompilePhase) {
        Map<CompilePhase, List<Matchable>> result = new HashMap<>();
        addCheckAttribute(failOnForCompilePhase, result);
        addCheckAttribute(countsForCompilePhase, result);
        return result;
    }

    private static void addCheckAttribute(Map<CompilePhase, Matchable> failOnForCompilePhase,
                                          Map<CompilePhase, List<Matchable>> result) {
        failOnForCompilePhase.forEach((compilePhase, matchable) -> {
            List<Matchable> list = result.computeIfAbsent(compilePhase, k -> new ArrayList<>());
            list.add(matchable);
        });
    }
}
