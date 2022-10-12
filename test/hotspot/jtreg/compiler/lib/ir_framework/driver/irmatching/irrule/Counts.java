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

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.ComparisonConstraintParser;
import compiler.lib.ir_framework.shared.TestFormat;

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
    public List<Constraint> constraints;

    private Counts(List<Constraint> constraints) {
        this.constraints = constraints;
    }

    public static Counts create(List<String> nodesWithCountConstraint, IRRule irRule) {
        List<Constraint> constraints = new ArrayList<>();
        int nodeId = 1;
        for (int i = 0; i < nodesWithCountConstraint.size(); i += 2, nodeId++) {
            String node = nodesWithCountConstraint.get(i);
            TestFormat.check(i + 1 < nodesWithCountConstraint.size(),
                             "Missing count " + getPostfixErrorMsg(irRule, node));
            String countConstraint = nodesWithCountConstraint.get(i + 1);
            Comparison<Integer> comparison = parseComparison(irRule, node, countConstraint);
            constraints.add(new Constraint(node, comparison, nodeId));
        }
        return new Counts(constraints);
    }

    private static String getPostfixErrorMsg(IRRule irRule, String node) {
        return "for IR rule " + irRule.getRuleId() + ", node \"" + node + "\" at " + irRule.getMethod();
    }

    private static Comparison<Integer> parseComparison(IRRule irRule, String node, String constraint) {
        String postfixErrorMsg = "in count constraint " + getPostfixErrorMsg(irRule, node);
        return ComparisonConstraintParser.parse(constraint, Integer::parseInt, postfixErrorMsg);
    }

    @Override
    public CheckAttributeMatchResult apply(String compilation) {
        CountsMatchResult result = new CountsMatchResult();
        checkConstraints(result, compilation);
        return result;
    }

    private void checkConstraints(CountsMatchResult result, String compilation) {
        for (Constraint constraint : constraints) {
            checkConstraint(result, compilation, constraint);
        }
    }

    private void checkConstraint(CountsMatchResult result, String compilation, Constraint constraint) {
        List<String> countsMatches = getCountsMatches(compilation, constraint);
        Comparison<Integer> comparison = constraint.comparison;
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
        return new CountsRegexFailure(constraint.nodeRegex, constraint.nodeId, countsMatches.size(), constraint.comparison,
                                      countsMatches);
    }

    static class Constraint {
        final String nodeRegex;
        final Comparison<Integer> comparison;
        private final int nodeId;

        Constraint(String nodeRegex, Comparison<Integer> comparison, int nodeId) {
            this.nodeRegex = nodeRegex;
            this.comparison = comparison;
            this.nodeId = nodeId;
        }
    }
}
