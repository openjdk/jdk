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
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irmethod.IRMethod;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.Counts;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.Constraint;
import compiler.lib.ir_framework.driver.irmatching.irrule.constraint.raw.RawConstraint;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This class creates {@link FailOn} and {@link Counts} objects for the default compile phase by parsing the raw
 * constraints. Each {@link RawConstraint} has a well-defined default phase. This class collects all parsed
 * {@link Constraint} objects for a compile phase in order to create {@link FailOn} and {@link Counts} objects for it.
 */
class DefaultPhaseRawConstraintParser {
    private final IRMethod irMethod;

    public DefaultPhaseRawConstraintParser(IRMethod irMethod) {
        this.irMethod = irMethod;
    }

    public Map<CompilePhase, List<Matchable>> parse(List<RawConstraint> rawFailOnConstraints,
                                                    List<RawConstraint> rawCountsConstraints) {
        Map<CompilePhase, Matchable> failOnForCompilePhase = parseRawConstraints(rawFailOnConstraints, FailOn::new);
        Map<CompilePhase, Matchable> countsForCompilePhase = parseRawConstraints(rawCountsConstraints, Counts::new);
        return mergeCheckAttributesForCompilePhase(failOnForCompilePhase, countsForCompilePhase);
    }

    private Map<CompilePhase, Matchable> parseRawConstraints(List<RawConstraint> rawConstraints,
                                                             BiFunction<List<Constraint>, String, Matchable> constructor) {
        Map<CompilePhase, List<Constraint>> constraintsForCompilePhase = new HashMap<>();
        for (RawConstraint rawConstraint : rawConstraints) {
            CompilePhase compilePhase = rawConstraint.defaultCompilePhase();
            List<Constraint> list = constraintsForCompilePhase.computeIfAbsent(compilePhase, k -> new ArrayList<>());
            list.add(rawConstraint.parse(compilePhase));
        }
        return replaceConstraintsByCheckAttribute(constructor, constraintsForCompilePhase);
    }

    private Map<CompilePhase, Matchable> replaceConstraintsByCheckAttribute(BiFunction<List<Constraint>, String, Matchable> constructor, Map<CompilePhase, List<Constraint>> constraintMap) {
        return constraintMap
                .entrySet()
                .stream()
                .map(e -> Map.entry(e.getKey(), constructor.apply(e.getValue(), irMethod.getOutput(e.getKey()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Map<CompilePhase, List<Matchable>> mergeCheckAttributesForCompilePhase(Map<CompilePhase, Matchable> failOnForCompilePhase,
                                                                                          Map<CompilePhase, Matchable> countsForCompilePhase) {
        Map<CompilePhase, List<Matchable>> result = new HashMap<>();
        failOnForCompilePhase.forEach((compilePhase, matchable) -> {
            List<Matchable> list = new ArrayList<>();
            list.add(matchable);
            result.put(compilePhase, list);
        });
        countsForCompilePhase.forEach((compilePhase, matchable) -> {
            List<Matchable> list = result.computeIfAbsent(compilePhase, k -> new ArrayList<>());
            list.add(matchable);
        });
        return result;
    }
}
