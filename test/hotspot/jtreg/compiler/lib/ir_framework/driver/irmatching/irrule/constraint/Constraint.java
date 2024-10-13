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

import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.driver.irmatching.MatchResult;
import compiler.lib.ir_framework.driver.irmatching.Matchable;
import compiler.lib.ir_framework.driver.irmatching.irrule.checkattribute.FailOn;
import compiler.lib.ir_framework.shared.Comparison;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This class represents a single constraint of a check attribute of an IR rule for a compile phase. It stores a
 * ready to be used regex for a compile phase (i.e. all {@link IRNode} placeholder strings are updated and composite nodes
 * merged) which can be used for matching. A {@link ConstraintCheck} decides if the matching on the constraint fails.
 *
 * @see FailOn
 */
public class Constraint implements Matchable {
    private final ConstraintCheck constraintCheck;
    private final String nodeRegex;
    private final String compilationOutput;

    private Constraint(ConstraintCheck constraintCheck, String nodeRegex, String compilationOutput) {
        this.constraintCheck = constraintCheck;
        this.nodeRegex = nodeRegex;
        this.compilationOutput = compilationOutput;
    }

    public static Constraint createFailOn(String nodeRegex, int constraintId, String compilationOutput) {
        return new Constraint(new FailOnConstraintCheck(nodeRegex, constraintId), nodeRegex, compilationOutput);
    }

    public static Constraint createCounts(String nodeRegex, int constraintId, Comparison<Integer> comparison,
                                          String compilationOutput) {
        return new Constraint(new CountsConstraintCheck(nodeRegex, constraintId, comparison), nodeRegex, compilationOutput);
    }

    public static Constraint createSuccess() {
        String nodeRegex = "impossible_regex";
        String compilationOutput = ""; // empty
        return new Constraint(new SuccessConstraintCheck(), nodeRegex, compilationOutput);
    }

    public String nodeRegex() {
        return nodeRegex;
    }

    @Override
    public MatchResult match() {
        List<String> matchedNodes = matchNodes(compilationOutput);
        return constraintCheck.check(matchedNodes);
    }

    private List<String> matchNodes(String compilationOutput) {
        Pattern pattern = Pattern.compile(nodeRegex);
        Matcher matcher = pattern.matcher(compilationOutput);
        return matcher.results().map(java.util.regex.MatchResult::group).collect(Collectors.toList());
    }
}
