/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.driver.irmatching.IRMatcher;
import compiler.lib.ir_framework.driver.irmatching.parser.CountsNodeRegex;
import compiler.lib.ir_framework.shared.Comparison;
import compiler.lib.ir_framework.shared.ComparisonConstraintParser;
import compiler.lib.ir_framework.shared.TestFormatException;

import java.util.ArrayList;
import java.util.List;

/**
 * This class provides default regex strings that can be used in {@link IR @IR} annotations to specify IR constraints.
 * <p>
 * There are two types of default regexes:
 * <ul>
 *     <li><p>Standalone regexes: Use them directly.</li>
 *     <li><p>Composite regexes: Their names contain "{@code _OF}" and expect another string in a list in
 *            {@link IR#failOn()} and {@link IR#counts()}. They cannot be use as standalone regex and will result in a
 *            {@link TestFormatException} when doing so.</li>
 * </ul>
 *
 * @see IR
 */
public class CountsNodeRegexParser {

    /**
     * Called by {@link IRMatcher} to merge special composite nodes together with additional user-defined input.
     */

    public static Counts parse(List<CountsNodeRegex> countsNodeRegexes, CompilePhase compilePhase) {
        if (countsNodeRegexes.isEmpty()) {
            return null;
        }
        List<CountsConstraint> countsConstraints = new ArrayList<>();
        int nodeId = 1;
        for (CountsNodeRegex countsNodeRegex : countsNodeRegexes) {
            CountsConstraint constraint = parseCountsConstraint(compilePhase, nodeId, countsNodeRegex);
            countsConstraints.add(constraint);
            nodeId++;
        }
        return new Counts(countsConstraints, compilePhase);
    }

    private static CountsConstraint parseCountsConstraint(CompilePhase compilePhase, int nodeId,
                                                          CountsNodeRegex countsNodeRegex) {
        String rawNodeString = countsNodeRegex.getRawNodeString();
        String parsedNodeString = NodeRegexParser.parseRawNodeString(compilePhase, countsNodeRegex, rawNodeString);
        String countConstraint = countsNodeRegex.getCountConstraint();
        Comparison<Long> comparison = parseComparison(rawNodeString, countConstraint);
        return new CountsConstraint(parsedNodeString, comparison, nodeId);
    }

    private static Comparison<Long> parseComparison(String node, String constraint) {
        try {
            return ComparisonConstraintParser.parse(constraint, Long::parseLong);
        } catch (TestFormatException e) {
            throw new TestFormatException(e.getMessage() + ", node \"" + node + "\",");
        }
    }
}
