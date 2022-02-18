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

package compiler.lib.ir_framework.driver.irmatching.irrule.constraint;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class represents a fully parsed {@link IR#counts()} attribute of an IR rule for a compile phase.
 *
 * @see IR#counts()
 * @see CheckAttribute
 */
public class Counts extends CheckAttribute<CountsConstraint, CountsMatchResult> {

    public Counts(List<CountsConstraint> constraints, String compilationOutput) {
        super(constraints, compilationOutput);
    }

    @Override
    protected CountsMatchResult createMatchResult() {
        return new CountsMatchResult();
    }

    @Override
    protected void checkConstraint(List<ConstraintFailure> constraintFailures, CountsConstraint constraint) {
        List<String> countsMatches = getMatchedNodes(constraint);
        Comparison<Integer> comparison = constraint.getComparison();
        if (!comparison.compare(countsMatches.size())) {
            constraintFailures.add(createRegexFailure(countsMatches, constraint));
        }
    }


    private CountsConstraintFailure createRegexFailure(List<String> countsMatches, CountsConstraint constraint) {
        return new CountsConstraintFailure(constraint.getRegex(), constraint.getIndex(), constraint.getComparison(), countsMatches);
    }
}
