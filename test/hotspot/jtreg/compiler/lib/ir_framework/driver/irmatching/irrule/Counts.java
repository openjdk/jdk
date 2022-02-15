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
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class representing a counts attribute of an IR rule.
 *
 * @see IR#counts()
 */
class Counts extends CheckAttribute {
    private final List<CountsConstraint> constraints;

    public Counts(List<CountsConstraint> constraints, CompilePhase compilePhase) {
        super(constraints, compilePhase);
        this.constraints = constraints;
    }


    @Override
    public CountsMatchResult apply(String compilation) {
        CountsMatchResult result = new CountsMatchResult();
        checkConstraints(result, compilation);
        return result;
    }

    private void checkConstraints(CountsMatchResult result, String compilation) {
        for (CountsConstraint constraint : constraints) {
            checkConstraint(result, compilation, constraint);
        }
    }

    private void checkConstraint(CountsMatchResult result, String compilation, CountsConstraint constraint) {
        List<String> countsMatches = getCountsMatches(compilation, constraint);
        Comparison<Integer> comparison = constraint.getComparison();
        if (!comparison.compare(countsMatches.size())) {
            result.addFailure(createRegexFailure(countsMatches, constraint));
        }
    }

    private List<String> getCountsMatches(String compilation, Constraint constraint) {
        Pattern pattern = Pattern.compile(constraint.nodeRegex);
        Matcher matcher = pattern.matcher(compilation);
        return matcher.results().map(MatchResult::group).collect(Collectors.toList());
    }

    private CountsRegexFailure createRegexFailure(List<String> countsMatches, Constraint constraint) {
        return new CountsRegexFailure(constraint.getNode(), constraint.getRegexNodeId(), foundCount,
                                      constraint.getComparison(), matches);
    }
}
